# funeral CLI `import` / `export` 改造计划

## 1. 背景与目标

### 1.1 当前问题

- `funeral push` / `funeral pull` 命名与 OCI 语义不一致，且源码把 image reference 中的域名当作 repository 命名空间，不符合标准 OCI 行为。
- 当前 CLI 只能操作 OCI layout 和 Docker tar，**不能**：
  - 直接读写 funeral server 的本地存储（文件目录或 MongoDB）
  - 从本机 Docker daemon 直接读写
  - 强制从指定 IP / 协议拉取或推送镜像
  - 同时推送到多个目标 IP

### 1.2 目标

- 用 `import` / `export` 替换 `push` / `pull`。
- 支持标准 OCI image reference 解析：`docker.xenoamess.com/a/b/c:1.1.1` → registry=`docker.xenoamess.com`，repository=`a/b/c`，tag=`1.1.1`。
- 支持强制指定 IP 和协议（`http://1.1.1.1`）。
- `export` 同时推送到多个目标。
- `import` / `export` 优先走本地 funeral server HTTP API，不可用时 fallback 到直接读/写文件存储，再 fallback 到 Docker daemon / OCI layout。

---

## 2. 最终命令格式

### 2.1 `import` — 从远程 registry 导入本地

```bash
funeral import docker.xenoamess.com/a/b/c:1.1.1 \
  --server http://1.1.1.1 \
  --to docker
```

### 2.2 `export` — 从本地导出到多个远程 registry

```bash
funeral export docker.xenoamess.com/a/b/c:1.1.1 \
  --to docker.xenoamess.com/a/b/c:1.1.1 \
  --to docker.xenoamess2.com/a/b/c:1.1.1 \
  --server https://1.1.1.1:443
```

### 2.3 参数说明

| 参数 | 作用 |
|------|------|
| `<image-ref>` | 标准 OCI image reference |
| `--to <local\|docker\|oci>` | `import` 输出目标类型（默认 `local`） |
| `--from <local\|docker\|oci>` | `export` 源类型（默认 `local`） |
| `--server <url>` | 强制指定远端 funeral server URL（覆盖 alias） |
| `--oci-dir <path>` | OCI layout 目录 |
| `--storage <path>` | 本地文件存储路径（默认 `/tmp/funeral-storage`） |
| `--use-docker` | `export` 强制使用 `docker push` |

---

## 3. 协议与 fallback 顺序

### 3.1 `export` 源（本地 → 远程）

按以下顺序尝试：

1. **本地 funeral server HTTP API**
   - 探测默认 registry 是否可达
   - 可用时通过 `FuneralClient` 拉取 manifest + blobs
2. **直接读 funeral 文件存储**
   - 仅当 backend 为 file storage（`no-mongo=true`，`no-minio=true`）
   - 从 `oci.storage.local-storage-path` 读取：
     - `manifests/*.json`
     - `blobs/sha256/<hex>`
3. **本机 Docker daemon**
   - `docker save <image>` → 临时 tar
   - tar 转 OCI layout
4. **OCI layout**
   - 从 `./<layout>` 读取

### 3.2 `import` 目标（远程 → 本地）

按以下顺序尝试：

1. **本地 funeral server HTTP API**
   - push 到默认 registry
2. **直接写 funeral 文件存储**
   - 仅当 backend 为 file storage
   - 写入 `manifests/`、`blobs/`、`Repository` 记录
3. **本机 Docker daemon**
   - 拉取到临时 OCI layout → 转 tar → `docker load`
4. **OCI layout**
   - 保存到 `./<layout>`

---

## 4. 详细实现阶段

### Phase 1: 标准 OCI image reference 解析

- 重写 `funeral-backend/src/main/java/io/oci/cli/oci/ImageReference.java`
- 支持：
  - `registry` / `repository` / `tag` / `digest` 拆分
  - 带端口 registry：`localhost:8911`
  - IPv6
  - digest 引用：`@sha256:...`
  - 默认 docker.io 和 `library/` 前缀（可选）

### Phase 2: 替换 CLI 子命令

- `funeral-backend/src/main/java/io/oci/cli/FuneralCommand.java`
  - 移除 `PushCommand.class`、`PullCommand.class`
  - 新增 `ImportCommand.class`、`ExportCommand.class`
- 删除 `PushCommand.java` / `PullCommand.java`（或保留但不再注册）
- 新增 `ImportCommand.java` / `ExportCommand.java`

### Phase 3: 本地 funeral server 探测

新增 `LocalServerProbe.java`：

```java
boolean isReachable(String registryUrl)
```

- 尝试连接默认 registry 的 health endpoint
- 可达返回 true，否则 false

### Phase 4: 文件存储直接读写

新增 `LocalStorageAdapter.java`：

- 读取 CLI 配置：
  - `oci.storage.local-storage-path`
  - `oci.storage.no-mongo`
  - `oci.storage.no-minio`
- 方法：
  - `readManifest(repository, reference)` → manifest bytes + mediaType
  - `readBlob(digest)` → bytes
  - `writeManifest(repository, reference, bytes, mediaType)`
  - `writeBlob(digest, bytes)`
  - `ensureRepository(repositoryName)`

实现时复用现有模型：
- `io.oci.model.Manifest`
- `io.oci.model.Blob`
- `io.oci.model.Repository`
- `FileStorageBase`（构造时注入 storagePath）

> 注意：直接写文件存储时要求本地 funeral server 未运行，避免文件竞争。

### Phase 5: Docker daemon 适配

新增 `DockerCliAdapter.java`：

- `boolean imageExists(String imageRef)`
- `void saveImage(String imageRef, Path tarOut)`
- `void loadImage(Path tarIn)`
- 使用 `ProcessBuilder` 调用本机 `docker` CLI

### Phase 6: Tar ↔ OCI layout 转换

- 从 `DockerTarResource.parseDockerTar` 抽取 tar 解析逻辑
- 新增 `DockerTarConverter.tarToOciLayout(Path tar, Path layout)`
- 新增 `OciLayoutConverter.ociLayoutToTar(Path layout, Path tar)`

### Phase 7: Registry 客户端增强

改造 `FuneralClient.java`：

- 新增 `hostOverride` 字段
- 请求时若 `hostOverride` 非空，设置 `Host` 头
- `baseUrl` 为实际连接地址（IP/协议）
- 认证凭证按 image reference 域名查找

改造 `CliHelper.java`：

- 新增 `createClient(registry, authDomain)` 重载
- 默认用 `authDomain`（image ref 域名）加载 credentials

### Phase 8: `import` 命令实现

1. 解析 image ref
2. 确定源 registry：
   - `--from-registry` 指定则用之
   - 否则用 image ref 的 registry
3. 确定目标：
   - 优先本地 funeral server HTTP
   - 次选文件存储
   - 再次 Docker daemon
   - 最后 OCI layout
4. 执行：
   - 远端拉取到临时 OCI layout
   - 根据目标类型写入对应位置

### Phase 9: `export` 命令实现

1. 解析 image ref
2. 确定源：
   - 优先本地 funeral server HTTP
   - 次选文件存储
   - 再次 Docker daemon
   - 最后 OCI layout
3. 解析 `--to-registry` 多目标
4. 对每个目标：
   - 创建 `FuneralClient`（连接地址 = target，Host = image ref 域名）
   - 用 `OciPushPull.push(repository, tag, layout)` 推送
5. 失败处理：
   - 默认：任一失败退出
   - `--continue-on-error`：继续并汇总

### Phase 10: 配置读取

CLI 读取配置方式：

- 优先：把 `ImportCommand` / `ExportCommand` 改为 CDI bean，注入 `@ConfigProperty`
- 备选：`ConfigProvider.getConfig()` 读取 `application.properties` / 环境变量

需要读取：
- `oci.storage.local-storage-path`
- `oci.storage.no-mongo`
- `oci.storage.no-minio`
- `oci.docker-local.docker-root`
- 默认 registry 配置

### Phase 11: 测试

新增/修改测试：

- `ImageReferenceTest`
- `LocalStorageAdapterTest`
- `DockerCliAdapterTest`
- `ImportCommandTest`
- `ExportCommandTest`
- 更新 `FuneralCliTest`（移除 push/pull 测试）

集成测试：

- 用 JDK `HttpServer` 模拟 registry
- 验证：
  - 请求路径 `/v2/a/b/c/manifests/1.1.1`
  - `Host: docker.xenoamess.com`
  - 协议为 `http`
  - 多目标部分失败

---

## 5. 文件改动清单

### 新增文件

- `funeral-backend/src/main/java/io/oci/cli/ImportCommand.java`
- `funeral-backend/src/main/java/io/oci/cli/ExportCommand.java`
- `funeral-backend/src/main/java/io/oci/cli/oci/LocalServerProbe.java`
- `funeral-backend/src/main/java/io/oci/cli/oci/LocalStorageAdapter.java`
- `funeral-backend/src/main/java/io/oci/cli/oci/DockerCliAdapter.java`
- `funeral-backend/src/main/java/io/oci/cli/oci/DockerTarConverter.java`
- `funeral-backend/src/main/java/io/oci/cli/oci/OciLayoutConverter.java`
- `funeral-backend/src/test/java/io/oci/cli/ImageReferenceTest.java`
- `funeral-backend/src/test/java/io/oci/cli/LocalStorageAdapterTest.java`
- `funeral-backend/src/test/java/io/oci/cli/DockerCliAdapterTest.java`
- `funeral-backend/src/test/java/io/oci/cli/ImportCommandTest.java`
- `funeral-backend/src/test/java/io/oci/cli/ExportCommandTest.java`

### 修改文件

- `funeral-backend/src/main/java/io/oci/cli/FuneralCommand.java`
- `funeral-backend/src/main/java/io/oci/cli/oci/ImageReference.java`
- `funeral-backend/src/main/java/io/oci/cli/oci/OciPushPull.java`
- `funeral-backend/src/main/java/io/oci/cli/client/FuneralClient.java`
- `funeral-backend/src/main/java/io/oci/cli/CliHelper.java`
- `funeral-backend/src/main/java/io/oci/cli/LoginCommand.java`（认证 key 改为域名）
- `funeral-backend/src/test/java/io/oci/cli/FuneralCliTest.java`

### 删除文件

- `funeral-backend/src/main/java/io/oci/cli/PushCommand.java`
- `funeral-backend/src/main/java/io/oci/cli/PullCommand.java`

---

## 6. 风险与注意事项

1. **文件存储直接写入风险**
   - 仅当 backend 为 file storage 时启用
   - 建议本地 funeral server 未运行时使用
   - 避免同时运行 server 和 CLI 写文件

2. **MongoDB / MinIO 不支持直接写入**
   - 此时 fallback 只能走 HTTP API
   - 若 server 未运行且 backend 为 MongoDB/MinIO，则报错

3. **Docker CLI 依赖**
   - Docker 协议需要本机安装 `docker` 命令
   - 未安装时自动 fallback 到 OCI layout

4. **标准 OCI 解析的兼容性**
   - 旧版 `push`/`pull` 将域名当作 repository 命名空间的行为将被废弃
   - 由于没有正式发布，可直接替换

---

## 7. 执行记录

本计划已执行完成，主要变更如下：

- 重写 `ImageReference` 为标准 OCI 解析（docker.io 默认、`library/` 前缀）。
- 新增 `LocalServerProbe`、`LocalStorageAdapter`、`DockerCliAdapter`、`DockerTarConverter`、`ImagePackager`。
- 修复 `DockerTarConverter.ociLayoutToTar` 未将 layer 路径写入 `manifest.json Layers` 数组的问题。
- 增强 `FuneralClient`：支持 `hostOverride` 并设置 `Host` 头；`CliHelper` 支持 image reference 与 alias 解析。
- 新增 `RegistryAlias` 配置与 `RegistryResolver` alias 解析。
- 实现 `import` / `export` 命令。
- 更新 `LoginCommand`：按 image reference 域名存储凭证。
- 新增 `LocalStorageAdapterTest`、`ImagePackagerTest`、`DockerTarConverterTest`。

实际命令格式见第 2 节，实现细节见代码。

提交记录：

1. `feat(cli): standard OCI image reference parsing and import/export adapters`
2. `feat(cli): implement import and export commands with multi-target support`
3. `test(cli): add tests for local storage, image packaging, and tar conversion`
