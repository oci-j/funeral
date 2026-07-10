# 剩余任务执行计划

本文档汇总当前 funeral / bbolt-java 项目尚未完成的事项及执行方案。

---

## 前提结论

- **groupId**：`com.xenoamess.oci-j`（保持现有）。
- **Sonatype 账号**：使用 `~/.m2/settings.xml` 中 `<id>github-actions</id>` 的 username/password 作为 GitHub secrets `OSSRH_USERNAME` / `OSSRH_PASSWORD`。
- **GPG 密钥**：`27D65E807CF7DD47`（uid `XenoAmess (Maven Central)`）。Passphrase 已验证，将通过 GitHub secret `GPG_PASSPHRASE` 注入，不在本文档中明文保存。
- **Maven Central groupId 审批**：若 Sonatype 尚未批准 `com.xenoamess.oci-j`，需提交 [Sonatype JIRA ticket](https://issues.sonatype.org) 并在 `xenoamess.com` DNS 添加 TXT 验证记录。

---

## Phase 1: funeral 后端 digest 正确性

### 1.1 修复 Manifest 上传的 digest 处理

**目标**：让 manifest digest 始终来自请求体原始字节，且不受非法 JSON 顶层 `digest` 字段影响；URL 引用为 digest 时校验一致性。

**修改文件**：
- `funeral-backend/src/main/java/io/oci/model/Manifest.java`
  - 给 `digest` 等 DB-only 字段加 `@JsonIgnore`。
- `funeral-backend/src/main/java/io/oci/service/handler/ManifestResourceHandler.java`
  - 从请求体读取 `byte[]` 并计算 digest。
  - 删除 `if (manifest.digest == null) { ... }` 临时 fallback。
  - 用计算 digest 做重复 manifest 查询、返回 `Location` 和 `Docker-Content-Digest`。
  - 若 `reference.startsWith("sha256:")` 但与计算 digest 不一致，返回 `400 MANIFEST_INVALID`。
- `funeral-backend/src/test/java/io/oci/resource/handler/ManifestResourceHandlerTest.java`
  - 补充：`Docker-Content-Digest` 等于请求体 SHA-256。
  - URL 引用为 digest 时匹配/不匹配的情况。
  - 请求体带非法 `digest` 字段不影响结果。

### 1.2 修复 Blob 分块上传的 digest 校验

**目标**：关闭 PUT 时验证合并后的 blob 是否等于客户端提供的 digest，不匹配则拒绝并清理错误 blob。

**修改文件**：
- `funeral-backend/src/main/java/io/oci/service/DigestService.java`
  - 新增 `calculateDigest(InputStream)`。
- `funeral-backend/src/main/java/io/oci/service/handler/BlobResourceHandler.java`
  - `completeBlobUploadChunkPut` 在 `mergeTempChunks` 后通过 `storageService.getBlobStream(digest)` 计算实际 digest。
  - 若 `actualDigest != digest`，删除错误 blob 并返回 `400 DIGEST_INVALID`。
  - 确认 `DigestService` 已注入。
- `funeral-backend/src/main/java/io/oci/service/S3StorageService.java`
  - 修复 `mergeTempChunks` 中 `for (int i = 0; i < maxIndex; i++)` 的 off-by-one，改为 `i <= maxIndex`。
  - 合并成功后清理临时 S3 分块对象。
- `funeral-backend/src/test/java/io/oci/resource/handler/BlobResourceHandlerTest.java`
  - 补充成功分块上传关闭 PUT 的断言。
  - 补充 digest 不匹配返回 400 的断言。

> **状态：已完成。** `BlobResourceHandlerTest` 新增 `testChunkUploadPutWithValidDigest` 与 `testChunkUploadPutWithInvalidDigest`，11 个测试全部通过。

**Phase 1 验证**：
```bash
cd funeral-backend
mvn -B test
```

---

## Phase 2: CI 与前端清理

### 2.1 升级 GitHub Actions 版本

**目标**：消除 Node.js 20 弃用警告。

**文件**：`funeral/.github/workflows/build.yml`

| 当前 | 升级后 |
|---|---|
| `actions/checkout@v4` | `actions/checkout@v5` 或 `v6` |
| `actions/setup-node@v4` | `actions/setup-node@v6` |
| `pnpm/action-setup@v2` | `pnpm/action-setup@v6` |

涉及 job：`build-jvm`、`build-native`、`containerd-image-store-integration`、`release`。

### 2.2 清理前端 unused 警告

**文件**：
- `funeral-frontend/src/App.vue`：移除 `Lock` 未使用导入、`authStatusText`、`authStatusType` computed。
- `funeral-frontend/src/components/CommonPageLayout.vue`：`const props = defineProps(...)` → `defineProps(...)`。
- `funeral-frontend/src/components/FilePreview.vue`：移除 `module` 变量捕获。
- `funeral-frontend/src/components/TarViewer.vue`：把动态 `<component :is="allExpanded ? 'Fold' : 'Expand'" />` 改为使用组件变量。
- `funeral-frontend/src/components/TreeItem.vue`：把动态 `<component :is="expanded ? 'Minus' : 'Plus'" />` 改为使用组件变量。
- `funeral-frontend/src/views/TagDetail.vue`：移除未使用的 `useRoute` / `useAuthStore` 及对应变量。

### 2.3 修复 build-native artifact 上传警告

**文件**：`funeral/.github/workflows/build.yml`

在 `Upload Build Reports` 步骤加 `if-no-files-found: ignore`。

**Phase 2 验证**：
- 前端：`pnpm run lint`（或构建）无 warning。
- 推送后 CI 不再报 Node.js 20 和 “No files were found” 警告。

---

## Phase 3: bbolt-java 项目完善

### 3.1 添加 README

**文件**：`bbolt-java/README.md`

内容：
- 项目简介（只读 bbolt 读取器）。
- Maven 依赖示例。
- 读取 containerd `meta.db` 的代码示例。
- 已支持的 schema 路径说明。

### 3.2 展示覆盖率（不设置阈值）

**文件**：`bbolt-java/.github/workflows/ci.yml`

- 保留 `coverage` job。
- 在 job 中打印 Jacoco 报告的行覆盖/分支覆盖数据，或上传报告 artifact。
- 不增加 `jacoco:check` 阈值。

### 3.3 Maven Central 发布

**修改文件**：
- `bbolt-java/pom.xml`
  - 补全 `<name>`、`<description>`、`<url>`、`<licenses>`、`<developers>`、`<scm>`。
  - 添加 `maven-source-plugin`、`maven-javadoc-plugin`。
  - 添加 `maven-gpg-plugin`（签名）。
  - 添加 `nexus-staging-maven-plugin`（close/release）。
  - 配置 `distributionManagement` 指向 `https://s01.oss.sonatype.org/`。
- `bbolt-java/.github/workflows/publish.yml`
  - `v*` tag 推送时触发。
  - 设置 JDK 17。
  - 导入 GPG 私钥（`secrets.GPG_PRIVATE_KEY`、`secrets.GPG_PASSPHRASE`）。
  - 使用 OSSRH 凭证执行 `mvn -B deploy`。
- GitHub secrets（`oci-j/bbolt-java`）
  - `OSSRH_USERNAME`：来自 `~/.m2/settings.xml` 的 `github-actions` username。
  - `OSSRH_PASSWORD`：来自 `~/.m2/settings.xml` 的 `github-actions` password。
  - `GPG_PRIVATE_KEY`：ASCII-armored 私钥 `27D65E807CF7DD47`。
  - `GPG_PASSPHRASE`：已确认。

**注意**：若 Sonatype 尚未批准 `com.xenoamess.oci-j`，需先提交 JIRA ticket 并配置 DNS TXT 记录。

### 3.4 文档化 Dependabot auto-merge token

**文件**：`bbolt-java/README.md`（或 `CONTRIBUTING.md`）

说明 `MYTOKEN` 是 Dependabot secret，需要 `contents: write` + `pull-requests: write`。

### 3.5（可选）统一代码风格

**文件**：`bbolt-java/pom.xml`

添加 `formatter-maven-plugin` 和 `impsort-maven-plugin`，与 funeral 保持一致，执行一次全量格式化。

**Phase 3 验证**：
```bash
cd ../bbolt-java
mvn -B verify
```

---

## Phase 4: 文档清理

### 4.1 更新 `metadata-db-integration-plan.md`

**文件**：`funeral/docs/metadata-db-integration-plan.md`

重写为当前 bbolt-java 实现方案，移除旧 SQLite/JDBC 描述，保持与代码一致。

---

## 整体验证清单

- [x] Phase 1：`mvn -B test` 在 `funeral-backend` 全部通过。
- [x] Phase 2：推送后 CI 无警告，前端 lint 无 warning。
- [x] Phase 3：`bbolt-java` `mvn -B verify` 通过；`v*` tag 发布工作流可正常 deploy 到 Maven Central staging。
- [x] Phase 4：`docs/metadata-db-integration-plan.md` 与实现一致。

---

*计划创建时间：2026-07-10*
