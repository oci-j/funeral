# 完整任务执行计划（all-tasks）

> 创建时间：2026-07-12  
> 目标：完成当前仓库所有已识别剩余任务，补齐测试、修复阻塞、重构核心模块并刷新文档。
> 执行开始时间：2026-07-13

---

## 已确认范围

1. **RegistryClient 抽取**：完整版，按 `docs/next-steps-plan.md` 执行。包括 `ImageReference` 公共化、`HttpRegistryClient` 实现、流式 `pullBlob`、统一认证/异常码。
2. **S3 测试**：优先使用 **in-memory S3 client** 覆盖业务逻辑；后续可视情况加 `localstack` 集成。

---

## 阶段 A：清阻塞与警告

| 编号 | 任务 | 涉及文件 | 验收标准 |
|---|---|---|---|
| A1 | 修复 `FilePreview.test.js` 失败 | `funeral-frontend/src/components/FilePreview.test.js` / `FilePreview.vue` | `pnpm test:unit FilePreview` 通过 |
| A2 | 修复前端剩余 eslint warning，取消 CI lint 宽容 | `funeral-frontend/src/views/TagDetail.test.js`、`Upload.test.js`、`.github/workflows/build.yml` | `pnpm run lint` 0 warning；CI lint 失败即停 |
| A3 | 修复 Maven 重定位警告 | `funeral-backend/pom.xml` | Maven 不再提示 `quarkus-junit5` 已重定位 |
| A4 | 清理 `MirrorHelmResource` 重复/私有 JAX-RS 注解 | `funeral-backend/src/main/java/io/oci/resource/MirrorHelmResource.java` | 测试日志不再出现 `isRegistryAccessible not public` 警告 |
| A5 | 处理未识别的 Quarkus 配置 | `funeral-backend/src/main/resources/application.yml` | 不再出现 `quarkus.http.cors` / `smallrye-openapi` 未识别警告 |
| A6 | 消除 LogManager 初始化错误 | `funeral-backend/pom.xml` | 测试日志不再出现 `LogManager accessed before...` |

---

## 阶段 B：RegistryClient 抽象与 MirrorResource 可测试化

按 `docs/next-steps-plan.md` 细化执行：

1. **新增 `RegistryClient` 接口**  
   文件：`funeral-backend/src/main/java/io/oci/registry/client/RegistryClient.java`  
   核心方法：
   - `ManifestResponse pullManifest(ImageReference ref, AuthContext auth)`
   - `InputStream pullBlob(ImageReference ref, String digest, AuthContext auth)`
   - `Optional<TokenResponse> authenticate(String wwwAuthenticate, ImageReference ref, AuthContext auth)`

2. **新增 `HttpRegistryClient` 实现**  
   文件：`funeral-backend/src/main/java/io/oci/registry/client/HttpRegistryClient.java`  
   职责：统一 `HttpClient`、处理 docker.io 匿名 token / Basic / Bearer 认证、解析 manifest list / OCI index 选择 `linux/amd64`、流式返回 blob。

3. **公共化 `ImageReference` 解析**  
   将解析逻辑提升到 `io.oci.model.ImageReference`，供 `MirrorResource`、`ImportCommand`、`ExportCommand`、`ContainerdFileResolver` 共用。

4. **重构 `MirrorResource`**  
   注入 `RegistryClient`，移除内联 `HttpClient` 调用；统一异常映射：
   - 网络超时 / 连接失败 → `502 BAD_GATEWAY`
   - 外部 Registry 认证失败 → `401` / `403`
   - 参数错误 → `400`
   - 其他内部错误 → `500`

5. **重写 `MirrorResourceTest`**  
   使用 `QuarkusMock`/`Mockito` 注入 mock `RegistryClient`：
   - `testMirrorSuccess`
   - `testMirrorManifestListSelectsLinuxAmd64`
   - `testMirrorWithAuth`
   - `testMirrorRegistryUnauthorized`
   - `testMirrorNetworkTimeout`

6. **拆分网络测试**  
   文件：`funeral-backend/src/test/java/io/oci/resource/MirrorResourceNetworkIT.java`  
   加 `@Tag("network")` 或 `@EnabledIfSystemProperty(named = "oci.test.network", matches = "true")`，默认不运行。

7. **更新 CI**  
   移除 `.github/workflows/build.yml` 中 `-Dtest='!*MirrorResourceTest'` 排除。

---

## 阶段 C：补齐后端测试

1. **新增 `MirrorHelmResourceTest`**  
   覆盖 `MirrorHelmResource` 的 OCI Helm 路径和 ChartMuseum 路径。

2. **新增 `S3StorageServiceTest`**  
   - 先实现 `InMemoryS3Client` 注入到 `S3StorageService`；
   - 覆盖合并分片 digest 校验、临时 chunk 清理、上传下载删除；
   - 可选后续加 `testcontainers`/`localstack` 集成。

---

## 阶段 D：提升前端函数覆盖率

针对以下组件/模块补充事件处理函数交互测试：

- `Admin.vue`
- `Mirror.vue`
- `MirrorHelm.vue`
- `Upload.vue`
- `Login.vue`
- `TagDetail.vue`
- `FilePreview.vue`
- `router/index.js`

目标：在保持现有行覆盖（95%）基础上，显著提升函数覆盖和分支覆盖。

---

## 阶段 E：CI 与真实集成

1. **native binary smoke test**  
   `build-native` 后执行 `./target/funeral --version` 或 health 检查。

2. **Docker push/pull 集成**  
   CI 中启动 registry，push 一个最小镜像再 pull 回来。

3. **严格化测试基线**  
   确保 `mvn -B test` 不再排除任何测试；`pnpm test:unit` 不再失败。

---

## 阶段 F：文档刷新

1. 标记或删除 `docs/remaining-tasks-plan.md`。
2. 更新 `CLAUDE.md`：当前仍写“无自动化测试”，与事实不符。
3. 更新 `README.md`：移除“artifact-type 未实现”等过时说明；可附带重新跑 OCI conformance 的结果。
4. 可选：新增 `docs/registry-client-design.md` 说明接口与扩展点。

---

## 建议提交顺序

1. `docs: add complete all-tasks execution plan`
2. `fix(frontend): FilePreview test and lint warnings`
3. `ci: fail frontend lint on warning and update Quarkus/Maven config`
4. `fix(backend): clean MirrorHelmResource annotations and application config warnings`
5. `refactor(backend): extract RegistryClient and refactor MirrorResource`
6. `test(backend): make MirrorResourceTest offline and add network-tagged integration`
7. `test(backend): add MirrorHelmResourceTest`
8. `test(backend): add S3StorageServiceTest with in-memory client`
9. `test(frontend): improve function coverage for key components`
10. `ci: add native binary smoke test and Docker push/pull integration`
11. `docs: refresh CLAUDE.md, README.md, and remove stale plans`

---

## 执行状态

| 阶段 | 状态 | 提交 SHA | 备注 |
|---|---|---|---|
| 计划入 docs | 完成 | d5f51e5 | 已存在 |
| A | 完成 | 84ac128 | 前端 lint、后端 warning、MirrorHelmResource 清理 |
| B | 完成 | a93d2e3 | RegistryClient 抽象 + MirrorResource 可测试化 |
| C | 完成 | ec6cc3f | MirrorHelmResourceTest、S3StorageServiceTest |
| D | 完成 | 1ae315f | 前端函数覆盖率 93.04% |
| E | 完成 | 5d4d940 | native smoke、Docker push/pull、CI 严格测试基线 |
| F | 未开始 | - | - |

---

*最后更新时间：2026-07-13*
