# 下一阶段执行计划：RegistryClient 抽象与 MirrorResource 可测试化

> ✅ 本计划已执行完成（2026-07-13）。各阶段完成状态与提交 SHA 见
> [`docs/complete-tasks-plan.md`](./complete-tasks-plan.md) 的执行状态表；
> 其中 §3.2 提到的 CLI 用法文档已由 [`docs/cli-usage.zh-CN.md`](./cli-usage.zh-CN.md) 落地。

## 目标

- 解决 `MirrorResourceTest` 依赖外部网络的问题，使 `mvn -B test` 不再排除任何测试即可全绿。
- 将 `MirrorResource` 中分散的远程 Registry HTTP 调用抽象为可注入、可测试的 `RegistryClient`。
- 补齐 S3 存储路径测试与文档，清理过期的剩余任务文档。

## 背景

`MirrorResource` 目前直接内联了 `HttpClient` 调用、Basic/Bearer 认证、Docker Hub 匿名 token、manifest list 平台选择等逻辑，导致：

- 单元测试必须访问 Docker Hub / 外部 Registry，本地与 CI 不稳定。
- 代码重复（每个请求都新建 `HttpClient`、重复构造 header、认证逻辑）。
- 内存占用高（`pullBlob` 把 blob 全部读进 `byte[]`）。
- 异常码不统一（外部超时、认证失败、内部错误混用 500）。

因此下一阶段优先重构这一层，并配套离线测试。

## 阶段一：抽象 `RegistryClient` 并重构 `MirrorResource`

### 1.1 新增接口

文件：`funeral-backend/src/main/java/io/oci/registry/client/RegistryClient.java`

核心方法：

```java
public interface RegistryClient {
    ManifestResponse pullManifest(ImageReference ref, AuthContext auth) throws IOException;
    InputStream pullBlob(ImageReference ref, String digest, AuthContext auth) throws IOException;
    Optional<TokenResponse> authenticate(String wwwAuthenticate, ImageReference ref, AuthContext auth) throws IOException;
}
```

### 1.2 新增实现

文件：`funeral-backend/src/main/java/io/oci/registry/client/HttpRegistryClient.java`

职责：

- 统一使用单个 `HttpClient` 实例（可配置连接池、超时）。
- 处理 `docker.io` 匿名 token、Basic Auth、Bearer 刷新、重定向。
- 解析 manifest list / OCI index，自动选择 `linux/amd64` 子 manifest。
- `pullBlob` 返回 `InputStream`，由调用方流式写入 storage。

### 1.3 改造 `MirrorResource`

文件：`funeral-backend/src/main/java/io/oci/resource/MirrorResource.java`

- 注入 `RegistryClient`。
- 移除所有直接 `HttpClient` 调用。
- 参数校验、组装 `ImageReference`、调用 `RegistryClient`、写入本地 storage、构造响应。
- 统一异常映射：
  - 网络超时 / 连接失败 -> `502 BAD_GATEWAY`
  - 外部 Registry 认证失败 -> `401` / `403`
  - 参数错误 -> `400`
  - 其他内部错误 -> `500`

### 1.4 统一镜像引用解析

将 `io.oci.cli.ImageReference` 或等价的解析逻辑提升到公共包 `io.oci.model.ImageReference`，供 `MirrorResource`、`ContainerdFileResolver`、`ImportCommand`、`ExportCommand` 共用。

### 1.5 顺手修复的小问题

- `MirrorResource.storeManifest` 中 `contentLength` 计算使用 `manifest.json.getBytes(StandardCharsets.UTF_8).length`。
- `tokenCache` 改用线程安全结构（如 `ConcurrentHashMap`）并考虑后续加 TTL。
- `pullAndStoreBlobs` 改为流式写入 `storageService.storeBlob(digest, inputStream, expectedSize)`。

## 阶段二：重写 `MirrorResourceTest`

文件：`funeral-backend/src/test/java/io/oci/resource/MirrorResourceTest.java`

### 2.1 保留测试

- `testMirrorWithoutSourceImage`
- `testMirrorWithEmptySourceImage`

### 2.2 新增离线测试

使用 `QuarkusMock` 或 `Mockito` 注入 mock `RegistryClient`：

- `testMirrorSuccess`：mock 返回 manifest + config + 2 layers，断言 `manifestStorage` / `blobStorage` 写入正确。
- `testMirrorManifestListSelectsLinuxAmd64`：mock 返回 index + 子 manifest + blobs，验证平台选择。
- `testMirrorWithAuth`：mock 验证 Basic Auth / Bearer 被正确传递。
- `testMirrorRegistryUnauthorized`：外部返回 401 -> 断言 401 或 403。
- `testMirrorNetworkTimeout`：mock 抛出 `HttpTimeoutException` -> 断言 502。

### 2.3 网络测试迁移

文件：`funeral-backend/src/test/java/io/oci/resource/MirrorResourceNetworkIT.java`

- 加 `@Tag("network")` 或 `@EnabledIfSystemProperty(named = "oci.test.network", matches = "true")`。
- 默认不运行，仅在 CI 显式启用网络时执行。

### 2.4 验证

```bash
cd funeral-backend
mvn -B test
```

目标：`MirrorResourceTest` 不再依赖网络，`mvn -B test` 全绿。

## 阶段三：补齐 S3 路径与文档

### 3.1 S3 存储测试

目标：让 `S3StorageService` 也有可重复测试。

文件：`funeral-backend/src/test/java/io/oci/service/S3StorageServiceTest.java`

可选方案：

| 方案 | 说明 |
|---|---|
| A | 使用 `testcontainers` / `localstack` 启动本地 S3，验证上传、下载、合并分片、删除。 |
| B | 新增 `InMemoryS3Client` 实现注入到 `S3StorageService`，聚焦业务逻辑。 |

推荐先用方案 B 覆盖合并分片 digest 校验、临时 chunk 清理等逻辑；再用方案 A 做集成验证。

### 3.2 文档清理

- `docs/remaining-tasks-plan.md`：已过期，标记为完成或删除。
- 新增/更新 `README.md` 或 `docs/cli-usage.md`：补充 `mirror`、`import`、`export` 用法。
- 可选：新增 `docs/registry-client-design.md` 说明 `RegistryClient` 接口与扩展点（如支持 harbor、gcr 等）。

### 3.3 前端 lint（如仍遗留）

- 检查 `build.yml` 中 `Lint frontend` 步骤是否仍带 `continue-on-error: true`。
- 修复剩余 eslint warning 后，去掉 `continue-on-error: true`，使 CI 对 lint 失败真正报错。

### 3.4 验证

```bash
# S3
cd funeral-backend
mvn -B test -Dtest=S3StorageServiceTest

# 前端
cd funeral-frontend
pnpm run lint
```

## 建议提交顺序

1. `refactor(backend): extract RegistryClient and refactor MirrorResource`
2. `test(backend): make MirrorResourceTest offline and add edge cases`
3. `test(backend): add S3 storage tests and update docs`

## 可选后续

- 运行 OCI Distribution 官方 conformance tests。
- 将 CLI 端到端测试接在真实 `QuarkusTest` 后端上。
- 切 `v0.1.0` tag，发布 native binary 和 JVM JAR。

---

*计划创建时间：2026-07-12*
