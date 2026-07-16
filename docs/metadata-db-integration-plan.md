# Containerd Image Store 集成计划（bbolt-java 方案）

> ✅ 本计划描述的功能已实现并合入主线（2026-07），内容已与当前代码核对一致。

## 目标

在 Docker Engine 29+ 启用 **containerd image store** 的场景下，即使 Docker Daemon 不可用，Funeral 也能通过读取宿主的 containerd metadata 把 `repo:tag` 解析为 manifest digest，并进一步读取 manifest 与 blob。

- 通过 [bbolt-java](https://github.com/oci-j/bbolt-java) 读取 containerd 的 bbolt 元数据库 `meta.db`。
- 从 containerd content store（`/var/lib/docker/containerd/daemon/io.containerd.content.v1.content/blobs`）读取 manifest 与 blob。
- 在 CI 中使用 Docker 29+ 的 Docker-in-Docker 容器生成真实的 `meta.db`，运行集成测试。
- **不污染开发环境**：所有 Docker 操作只在 CI 的临时容器中进行，不修改 runner 主 Docker、也不修改本地 Docker 配置。

## 背景与关键结论

- Docker Engine 29+ 启用 **containerd image store** 后，镜像元数据不再放在 overlay2 graphdriver，而是使用 containerd 的 bbolt 数据库。
- 该数据库路径通常为 `/var/lib/docker/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db`（宿主机上），也可能在单独的 containerd root 下。
-  funeral 不再依赖 SQLite/JDBC 读取元数据，而是通过 `bbolt-java`（只读 Java bbolt 读取器）直接访问 `meta.db`。
- `Overlay2FileResolver` 继续通过 `repositories.json` 服务 legacy overlay2 场景。

## 实现结构

### 1. `io.oci.docker.containerd.MetadataDbImageIdFinder`

- 使用 `bbolt-java` 打开 `meta.db`。
- 按固定 schema 读取 image digest：
  - `v1/moby/images/<name>/target/digest`
- 支持多个候选名称：
  - `repo:tag`
  - `docker.io/library/repo:tag`
  - `docker.io/repo:tag`
- 优先检查 `containerdRoot/io.containerd.metadata.v1.bolt/meta.db`，再检查 `dockerRoot/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db`。
- 返回 `Optional<String>` digest，失败时优雅回退，不抛异常。

### 2. `io.oci.docker.ContainerdFileResolver`

- 注入 `MetadataDbImageIdFinder`。
- `resolveManifest(repositoryName, reference)` 逻辑：
  - 如果 `reference` 以 `sha256:` 开头，按原路径直接读取 content store。
  - 否则视为 tag，调用 `MetadataDbImageIdFinder` 查 digest，再用该 digest 读取 manifest。
- `resolveBlob(digest)` 直接从 content store 读取 blob 文件。
- 返回 `Optional.empty()` 表示无法解析。

### 3. 单元测试

- `MetadataDbImageIdFinderTest`：基于内嵌的 fixture `meta.db` 验证 `repo:tag` → digest 解析。
- `ContainerdFileResolverTest`：测试 digest 路径解析和 media type 检测。
- `DockerLocalResolverTest`：测试 digest 缓存与 fallback 链。

### 4. CI 集成测试：`containerd-image-store-integration`

`.github/workflows/build.yml` 中已包含该 job，流程如下：

```bash
# 启动 Docker 29+ dind（若 29 不存在则回退 latest）
docker run -d --name dind \
  -e DOCKER_TLS_CERTDIR="" \
  --privileged \
  docker:29-dind || docker run -d --name dind \
  -e DOCKER_TLS_CERTDIR="" \
  --privileged \
  docker:dind

# 等待 daemon 就绪
for i in {1..15}; do
  docker exec dind docker info >/dev/null 2>&1 && break
  sleep 2
done

# 拉取测试镜像并确认 containerd image store 已启用
docker exec dind docker pull alpine:3.20
docker exec dind docker info -f '{{ .DriverStatus }}' | grep -qi containerd

# 停止 daemon 以 flush bbolt 数据
docker exec dind sh -c 'kill -TERM $(cat /var/run/docker.pid 2>/dev/null || pidof dockerd)' || true
docker wait dind || true

# 复制 meta.db 与 content store 到测试目录
mkdir -p ./funeral-backend/target/real-docker-root/containerd/daemon/io.containerd.metadata.v1.bolt
docker cp "dind:/var/lib/docker/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db" \
  ./funeral-backend/target/real-docker-root/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db
mkdir -p ./funeral-backend/target/real-containerd-blobs
docker cp "dind:/var/lib/docker/containerd/daemon/io.containerd.content.v1.content" \
  ./funeral-backend/target/real-containerd-blobs/

docker rm -f dind
```

然后运行集成测试：

```bash
mvn -B test -Dtest=ContainerdFileResolverIntegrationTest \
  -Doci.docker-local.real-metadata-db=target/real-docker-root/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db \
  -Doci.docker-local.real-containerd-root=target/real-containerd-blobs
```

测试内部使用 `assumeTrue` 判断 fixture 文件存在；本地没有这些文件时自动跳过，不强制要求开发者环境。

### 5. Targeted 测试

`build-jvm` job 末尾运行与本次功能相关且不会触发环境依赖失败用例的测试：

```bash
mvn -B test -Dtest=MetadataDbImageIdFinderTest,DockerLocalResolverTest,Overlay2FileResolverTest,ContainerdFileResolverTest
```

### 6. Native Build 验证

- 运行 `mvn -B clean package -Pnative -DskipTests` 确保 `bbolt-java` 与新增代码不会破坏 native 编译。
- `bbolt-java` 无 native 依赖，通常不需要额外 native-image 配置。

## 依赖

- `bbolt-java` 作为 funeral 的依赖（SNAPSHOT 或 release 版本）。
- CI 中通过 `mvn -B -f ../bbolt-java/pom.xml install -DskipTests` 先安装 `bbolt-java`。

## 风险与应对

| 风险 | 应对 |
|------|------|
| `meta.db` 路径/schema 变化 | 同时检查 `containerdRoot` 与 `dockerRoot` 下的 `meta.db`；schema 变化时更新 `MetadataDbImageIdFinder` |
| dind 默认未启用 containerd image store | CI 脚本中检查 `docker info`；若未启用则失败 |
| CI 运行时间变长 | 只拉取一个极小镜像（`alpine:3.20`），且 job 与 build-native 并行 |
| bbolt-java 版本未发布 | CI 中先 `mvn install` 本地版本；发布到 Maven Central 后改用正式版本 |

## 验收标准

- `MetadataDbImageIdFinder` 能通过 fixture `meta.db` 测试。
- `ContainerdFileResolver` 在 CI 中通过真实 `meta.db` 解析 `alpine:3.20` 并返回正确 manifest。
- `DockerLocalResolver` 的 fallback 链与缓存测试通过。
- Native build 不因此功能失败。
- `bbolt-java` 可独立 `mvn -B verify` 通过。
