# 剩余任务执行计划（当前迭代）

本文档记录本次迭代需要完成的剩余任务： funeral backend Phase 1.2 收尾、文档同步、Dependabot 配置调整。

---

## 任务 1：Phase 1.2 — Blob 分块上传关闭 PUT 时的 digest 校验

### 目标

在 `BlobResourceHandler.completeBlobUploadChunkPut` 中，`mergeTempChunks` 合并完所有分块后，必须计算最终 blob 的实际 SHA-256 digest，并与客户端在 URL 参数中提供的 `digest` 进行比对。

- 一致 → 201 Created，返回 `Location` 与 `Docker-Content-Digest`。
- 不一致 → 400 `DIGEST_INVALID`，删除已合并的错误 blob。

### 实现细节

1. **新增 `DigestService.calculateDigest(InputStream)`**
   - 文件：`funeral-backend/src/main/java/io/oci/service/DigestService.java`
   - 行为：读取 InputStream 并计算 SHA-256，返回 `sha256:<hex>`。
   - 实现时使用固定缓冲区（如 8KB），避免大 blob 占用过多内存。

2. **注入 `DigestService` 到 `BlobResourceHandler`**
   - 文件：`funeral-backend/src/main/java/io/oci/service/handler/BlobResourceHandler.java`
   - 在现有 `@Inject` 字段后新增 `DigestService digestService`。

3. **修改 `completeBlobUploadChunkPut`**
   - 在 `storageService.mergeTempChunks(uploadUuid, index, digest)` 之后：
     - 通过 `storageService.getBlobStream(digest)` 读取已合并 blob。
     - 调用 `digestService.calculateDigest(InputStream)` 得到 `actualDigest`。
     - 比对 `actualDigest` 与 `digest`：
       - 若相等，继续原有逻辑（写入 `Blob` 元数据、返回 201）。
       - 若不相等：
         - 调用 `storageService.deleteBlob(digest)` 删除错误 blob。
         - 返回 `Response.status(400)` + `ErrorResponse`（code: `DIGEST_INVALID`）。

4. **修复 `S3StorageService.mergeTempChunks` 的已知问题**
   - 文件：`funeral-backend/src/main/java/io/oci/service/S3StorageService.java`
   - 将 `for (int i = 0; i < maxIndex; i++)` 改为 `for (int i = 0; i <= maxIndex; i++)`，修复 off-by-one。
   - 在 `composeObject` 成功后，遍历并删除 `tempBucketName` 中 `chunk/<uploadUuid>/<i>` 临时对象。

### 涉及文件

- `funeral-backend/src/main/java/io/oci/service/DigestService.java`
- `funeral-backend/src/main/java/io/oci/service/handler/BlobResourceHandler.java`
- `funeral-backend/src/main/java/io/oci/service/S3StorageService.java`
- `funeral-backend/src/test/java/io/oci/resource/handler/BlobResourceHandlerTest.java`

---

## 任务 2：补充测试用例

在 `BlobResourceHandlerTest` 中新增两个测试：

### 2.1 `testChunkUploadPutWithValidDigest`

流程：
1. `POST /v2/{name}/blobs/uploads/` 开启上传会话，获取 `Docker-Upload-UUID`。
2. `PUT /v2/{name}/blobs/uploads/{uuid}/0_0?digest=<actualDigest>` 上传最终 chunk。
3. 断言：
   - HTTP 状态码为 201。
   - 响应头 `Docker-Content-Digest` 等于上传前计算出的 digest。

### 2.2 `testChunkUploadPutWithInvalidDigest`

流程同上，但 URL 中的 `digest` 故意填错（如 `sha256:000000...`）。

断言：
- HTTP 状态码为 400。
- 响应 body 中包含 `DIGEST_INVALID`。

### 验证命令

```bash
cd funeral-backend
mvn -B test -Dtest=BlobResourceHandlerTest
```

---

## 任务 3：更新 `docs/execution-plan.md`

将整体验证清单中 Phase 1–Phase 4 的复选框标记为已完成，并在 Phase 1.2 小节补充说明 digest 校验已实现、测试已补充。

涉及文件：
- `docs/execution-plan.md`

---

## 任务 4：Dependabot 开放 PR 上限到 100

修改 `.github/dependabot.yml`：

- `package-ecosystem: maven`：`open-pull-requests-limit` 从 10 改为 100。
- `package-ecosystem: npm`：`open-pull-requests-limit` 从 10 改为 100。
- `package-ecosystem: github-actions`：`open-pull-requests-limit` 从 5 改为 100。
- `package-ecosystem: docker`：`open-pull-requests-limit` 从 5 改为 100。

涉及文件：
- `.github/dependabot.yml`

---

## 提交计划

建议按以下顺序提交，方便回滚与审阅：

1. `fix(backend): validate digest after chunked blob upload and fix S3 merge cleanup`
2. `test(backend): add BlobResourceHandlerTest for chunk upload digest validation`
3. `docs: mark execution plan phases as completed`
4. `ci(dependabot): increase open-pull-requests-limit to 100`

---

*计划创建时间：2026-07-11*
