# import/export 集成测试与增强计划

## 目标

- 用 JDK `HttpServer` 搭建 mock registry，覆盖 `import` 和 `export` 端到端流程。
- 验证 `Host` 头覆盖、多目标推送等关键行为。
- 在 `import`/`export` 中增加 `--continue-on-error` 和 `--platform` 增强。

---

## 1. 集成测试设计

### 1.1 新增 `MockRegistryServer`（测试工具类）

基于 `com.sun.net.httpserver.HttpServer`：

- 支持路径：
  - `GET /v2/token`：直接返回 mock token（可选 basic auth）。
  - `GET /v2/<name>/manifests/<ref>`：返回预置 manifest 和 `Content-Type`。
  - `GET /v2/<name>/blobs/<digest>`：返回预置 blob。
  - `POST /funeral_addition/write/upload/dockertar`：接收 multipart，保存 tar 体用于断言。
- 暴露方法：
  - `String baseUrl()`
  - `List<String> recordedHostHeaders()`
  - `byte[] lastUploadedTarBytes()`
  - `int uploadCount()`

### 1.2 测试数据

构造一个最小 OCI image：

- config blob：`{"architecture":"amd64","os":"linux","config":{},"rootfs":{"type":"layers","diff_ids":[]}}`
- layer blob：`layer-content`
- manifest：OCI image manifest，引用 config / layer digest。

### 1.3 测试用例

1. `testImportToLocalStorage`
   - `import docker.xenoamess.com/test/repo:1.0 --to local --storage <temp> --server <mock>`
   - 断言 temp 目录下写入 manifest 和 blob。

2. `testImportToOciLayout`
   - `import docker.xenoamess.com/test/repo:1.0 --to oci --oci-dir <temp> --server <mock>`
   - 断言 `index.json`、manifest blob、config blob、layer blob 存在。

3. `testExportFromLocalToFuneralServer`
   - 先往本地 storage 写 manifest 和 blob。
   - `export docker.xenoamess.com/test/repo:1.0 --to docker.xenoamess.com/test/repo:1.0 --from local --storage <temp> --server <mock>`
   - 断言 mock server 收到一次上传，tar 可被 `DockerSaveTarParser` 解析，且 `RepoTags` 包含目标 ref。

4. `testExportHostHeaderOverride`
   - 同上，断言 mock server 收到的请求头 `Host: docker.xenoamess.com`。

5. `testExportToMultipleTargets`
   - 启动两个 mock server，分别 `--to` 两个目标。
   - 断言两个 server 都收到上传。

6. `testImportWithBasicAuth`（可选）
   - mock server token 端点要求 basic auth。
   - 通过 `ConfigManager` 写入 `auths` 凭证。
   - 断言导入成功。

### 1.4 新增文件

- `funeral-backend/src/test/java/io/oci/cli/ImportExportIntegrationTest.java`
- `funeral-backend/src/test/java/io/oci/cli/oci/MockRegistryServer.java`（或作为内部类）

---

## 2. import/export 增强

### 2.1 `--continue-on-error`

- `ExportCommand` 新增 `--continue-on-error` flag。
- 每个 target 单独 try-catch，记录失败 target，最后汇总。
- 任一失败时返回非 0，但不中断后续目标。

### 2.2 `--platform`

- `ImportCommand` / `ExportCommand` 新增 `--platform <os>/<arch>`（如 `linux/amd64`）。
- `ImagePackager.resolveImageManifest` 支持从 OCI index 按 `platform` 选择子 manifest。
- 未指定时仍取第一个 image manifest（保持兼容）。

---

## 3. 验证与提交

```bash
cd funeral-backend
mvn -B test -Dtest=ImportExportIntegrationTest
mvn -B test -Dtest='!*MirrorResourceTest'
```

提交计划：

1. `test(cli): add mock registry server and import/export integration tests`
2. `feat(cli): add --continue-on-error to export command`
3. `feat(cli): add --platform support for multi-arch image references`

---

*计划创建时间：2026-07-12*
