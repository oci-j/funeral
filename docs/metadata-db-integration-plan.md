# Metadata DB 集成与 CI 真实 DB 测试计划

## 目标

让 Funeral 在 Docker Engine 29+ 的 **containerd image store** 场景下，也能在 Docker Daemon 不可用时按 `repo:tag` 解析镜像：

- 通过读取 `/var/lib/docker/image/overlay2/metadata.db` 或 `/var/lib/docker/image/overlayfs/metadata.db`，把 `repo:tag` 映射到 manifest/config digest。
- 再从容器的 containerd content store（`/var/lib/containerd/io.containerd.content.v1.content/blobs`）读取 manifest 与 blob。
- 在 CI 中使用 Docker 29+ 的 Docker-in-Docker 容器生成真实的 `metadata.db`，运行集成测试。
- **不污染开发环境**：所有 Docker 操作只在 CI 的临时容器中进行，不修改 runner 主 Docker、也不修改本地 Docker 配置。

## 背景与关键结论

- `metadata.db` 是 Docker Engine 29+ 启用 **containerd image store** 后才会出现的 SQLite 元数据库。
- 它属于 containerd image store 路径，而不是 legacy overlay2 graphdriver。因此它应该集成到 `ContainerdFileResolver`，而不是 `Overlay2FileResolver`。
- `Overlay2FileResolver` 继续通过 `repositories.json` 服务 legacy overlay2 场景。
- 当前 `ContainerdFileResolver` 只能按 `sha256:...` digest 读取 blob，无法按 tag 解析。`metadata.db` 正是用来补齐这个缺口。

## 实现步骤

### 1. 新增 `io.oci.docker.containerd.MetadataDbImageIdFinder`

- 连接 SQLite `metadata.db`。
- 使用 `PRAGMA table_info` / `sqlite_master` 自动探测数据库 schema。
- 支持多套候选查询策略，按优先级尝试：
  - `SELECT image_id FROM images WHERE name = ?`
  - `SELECT id FROM images WHERE name = ?`
  - `SELECT digest FROM images WHERE name = ?`
  - `SELECT value FROM kv WHERE key = ?`（并解析 JSON 中的 `target.digest`）
  - 从 JSON 字段中直接提取 `target.digest`
- 同时检查两个可能路径：
  - `dockerRoot/image/overlay2/metadata.db`
  - `dockerRoot/image/overlayfs/metadata.db`
- 输入 tag 时尝试多种 repo name 形式（`alpine:3.20`、`docker.io/library/alpine:3.20`、`docker.io/alpine:3.20` 等）。
- 返回 `Optional<String>` digest，失败时优雅回退，不抛异常。

### 2. 改造 `ContainerdFileResolver`

- 新增 `dockerRoot` 配置注入（默认 `/var/lib/docker`）。
- 注入 `MetadataDbImageIdFinder`（或使用 new 创建，保持 CDI 兼容）。
- `resolveManifest(String reference, String repositoryName)` 逻辑：
  - 如果 `reference` 以 `sha256:` 开头，按原路径直接读取 content store。
  - 否则视为 tag，调用 `MetadataDbImageIdFinder` 查 digest，再用该 digest 读取 manifest。
- 如果查不到或数据库不存在，返回 `Optional.empty()`。

### 3. 单元测试

- `MetadataDbImageIdFinderTest`
  - 在 `@TempDir` 下创建临时 SQLite DB，写入常见 schema 数据，验证 `repo:tag` → digest 解析。
  - 验证找不到时返回 empty。
  - 验证 schema 未知时不抛异常。
- 扩展 `DockerLocalResolverTest`
  - 增加测试：当客户端先 HEAD 再 GET by digest 时，第二次请求命中缓存，避免重新查 DB/content store。

### 4. CI 新增 job：`containerd-image-store-integration`

使用临时 Docker-in-Docker 容器生成真实 `metadata.db`：

```bash
# 启动 Docker 29+ dind（显式指定版本，若不存在则回退 latest）
docker run -d --name dind \
  -e DOCKER_TLS_CERTDIR="" \
  --privileged \
  docker:29-dind || \
docker run -d --name dind \
  -e DOCKER_TLS_CERTDIR="" \
  --privileged \
  docker:dind

# 等待 daemon 就绪
for i in 1 2 3 4 5 6 7 8 9 10; do
  docker exec dind docker info >/dev/null 2>&1 && break
  sleep 2
done

# 拉取测试镜像，并确认启用 containerd image store
docker exec dind docker pull alpine:3.20
docker exec dind docker info -f '{{ .DriverStatus }}' | grep -i containerd

# 定位 metadata.db 路径
DB_PATH=$(docker exec dind find /var/lib/docker/image -name metadata.db -print 2>/dev/null | head -1)

# 停止 daemon 以 flush SQLite
docker exec dind sh -c 'kill -TERM $(cat /var/run/docker.pid 2>/dev/null || pidof dockerd)'
docker wait dind || true

# 复制 metadata.db 到测试目录
mkdir -p ./funeral-backend/target/real-db
docker cp "dind:$DB_PATH" ./funeral-backend/target/real-db/metadata.db
docker cp "dind:${DB_PATH}-wal" ./funeral-backend/target/real-db/metadata.db-wal 2>/dev/null || true
docker cp "dind:${DB_PATH}-shm" ./funeral-backend/target/real-db/metadata.db-shm 2>/dev/null || true

# 同时复制本次拉取用到的 containerd content store  blobs（用于完整解析测试）
mkdir -p ./funeral-backend/target/real-containerd-blobs/sha256
docker cp dind:/var/lib/containerd/io.containerd.content.v1.content/blobs/sha256 \
  ./funeral-backend/target/real-containerd-blobs/ 2>/dev/null || true

# 清理临时容器
docker rm -f dind
```

然后运行集成测试：

```bash
mvn -B test -Dtest=ContainerdFileResolverIntegrationTest \
  -Doci.docker-local.real-metadata-db=target/real-db/metadata.db \
  -Doci.docker-local.real-containerd-root=target/real-containerd-blobs
```

测试内部用 `assumeTrue` 判断文件存在；本地没有这些文件时自动跳过，不强制要求开发者环境。

### 5. 在 `build-jvm` 末尾加 targeted tests

只运行本次涉及且不会触发已有失败用例的测试：

```bash
mvn -B test -Dtest=MetadataDbImageIdFinderTest,DockerLocalResolverTest,Overlay2FileResolverTest,ContainerdFileResolverTest
```

### 6. Native Build 验证

- 运行 `mvn -B clean package -Pnative -DskipTests` 确保新增 SQLite/JDBC 代码不会破坏 native 编译。
- 如有 `java.sql` / `org.sqlite` 反射缺失，补充 `src/main/resources/META-INF/native-image/...` 配置。

### 7. 提交与推送

按 `AGENTS.md` 默认规则：

- 阶段一 commit：`feat: add metadata.db image id finder for containerd image store`
- 阶段二 commit：`ci: add Docker 29+ dind metadata.db integration test`
- 阶段三 commit：`test: targeted unit tests for docker fallback chain`
- 或合并为一个 PR 提交。

## 风险与应对

| 风险 | 应对 |
|------|------|
| 真实 `metadata.db` schema 未知 | 实现灵活的 schema 探测；CI 运行后打印 schema 以便必要时调整 |
| `sqlite-jdbc` 在 native image 中需要额外 reflection | 本地 native build 验证，补充 reachability metadata |
| dind 默认未启用 containerd image store | CI 脚本中检查 `docker info`；若未启用，回退写入 `daemon.json` 并重启 |
| CI 运行时间变长 | 只拉取一个极小镜像（`alpine:3.20`），且 job 与 build-native 并行 |

## 验收标准

- `MetadataDbImageIdFinder` 能通过合成 DB 测试。
- `ContainerdFileResolver` 在 CI 中通过真实 `metadata.db` 解析 `alpine:3.20` 并返回正确 manifest。
- `DockerLocalResolver` 的 digest 缓存测试通过。
- Native build 不因此功能失败。
- 所有改动提交并推送。
