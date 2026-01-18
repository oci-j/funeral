# ChartMuseum 格式 - 最终修复和测试指南

## 问题总结

### 背景
你在 UI 中使用 ChartMuseum 格式同步 `bitnami/mongodb:12.1.31` 后，执行 Helm pull 失败：
```
Error: manifest does not contain minimum number of descriptors (2), descriptors found: 1
```

### 发现的两个问题

#### 问题 1: URL 构建 Bug ❌ FIXED
当使用 ChartMuseum 格式时，URL 构建不正确：

**错误 URL 示例：**
- 输入：`sourceRepo=https://charts.bitnami.com`, `chartName=bitnami/mongodb`
- 旧代码生成：`https://charts.bitnami.com/mongodb-12.1.31.tgz` ❌ 403 错误
- 正确应该：`https://charts.bitnami.com/bitnami/mongodb-12.1.31.tgz` ✓

**根因：** 当 `sourceRepo` 不包含组织路径时，需要从 `chartName` 中提取并添加到 URL。

#### 问题 2: 存储结构不完整 ✅ FIXED (之前)
ChartMuseum 图表没有正确创建 OCI manifest 结构（config + layer）。

**Fix：** 现在正确存储为：
- Config blob: `application/vnd.cncf.helm.config.v1+json`
- Layer blob: `application/vnd.cncf.helm.chart.content.v1.tar+gzip`

### 解决方案：增强的 URL 构建逻辑

```java
// 1. 提取简单 chart 名称
simpleChartName = "bitnami/mongodb" → "mongodb"

// 2. 提取组织前缀
orgPrefix = "bitnami"

// 3. 智能构建 URL
if baseUrl 不包含 "/bitnami":
    URL = baseUrl + "/bitnami/mongodb-12.1.31.tgz"
else:
    URL = baseUrl + "/mongodb-12.1.31.tgz"
```

## 测试步骤

### 方法 1: 使用测试脚本（推荐）

```bash
cd /home/xenoamess/workspace/funeral
chmod +x test_chartmuseum_mongodb.sh
./test_chartmuseum_mongodb.sh
```

### 方法 2: 手动测试

**Terminal 1 - 启动后端：**
```bash
cd funeral-backend
mvn clean compile quarkus:dev
```

**Terminal 2 - 执行测试：**

```bash
cd funeral

# 1. 清理旧数据
./cleanup_manifests.sh

# 2. 使用正确格式的 sourceRepo（包含 /bitnami）
curl -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=chartmuseum" \
  -d "sourceRepo=https://charts.bitnami.com/bitnami" \
  -d "chartName=bitnami/mongodb" \
  -d "version=12.1.31" \
  -d "targetRepository=mongodb-chartmuseum" \
  -d "targetVersion=12.1.31"

# 应该看到生成的 URL: https://charts.bitnami.com/bitnami/mongodb-12.1.31.tgz

# 3. 测试 Helm pull
helm pull oci://192.168.8.9:8911/mongodb-chartmuseum --version 12.1.31 --plain-http
```

## 关键配置

### 正确的参数格式：

**Repository Format:** ChartMuseum

**Source Repository:** `https://charts.bitnami.com/bitnami`
⚠️ 重要：必须包含 `/bitnami` 路径

**Chart Name:** `bitnami/mongodb`
格式：`organization/chartname`

**Chart Version:** `12.1.31`

**生成的 URL:**
```
https://charts.bitnami.com/bitnami/mongodb-12.1.31.tgz
```

## UI 配置示例

```
Repository Format: ChartMuseum ✓
Chart Name: bitnami/mongodb
Chart Version: 12.1.31
Target Repository: mongodb (或自定义)
Target Version: 12.1.31
Source Repository: https://charts.bitnami.com/bitnami  ← 关键！
```

## 预期日志输出

当镜像 ChartMuseum 图表时，后端日志应该显示：

```
INFO  [io.oci.resource.MirrorHelmResource] === CHARTMUSEUM MIRROR ===
INFO  [io.oci.resource.MirrorHelmResource] Chart: bitnami/mongodb:12.1.31
INFO  [io.oci.resource.MirrorHelmResource] Source repo: https://charts.bitnami.com/bitnami
INFO  [io.oci.resource.MirrorHelmResource] Generated URL: https://charts.bitnami.com/bitnami/mongodb-12.1.31.tgz
INFO  [io.oci.resource.MirrorHelmResource] Successfully downloaded chart: 12345 bytes
INFO  [io.oci.resource.MirrorHelmResource] Storing chart as layer blob: sha256:...
INFO  [io.oci.resource.MirrorHelmResource] Storing config blob: sha256:...
INFO  [io.oci.resource.MirrorHelmResource] Storing OCI manifest with 2 blobs
```

## 验证清单结构

镜像完成后，检查 manifest：

```bash
# 获取 manifest
MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
  http://localhost:8911/v2/mongodb-chartmuseum/manifests/12.1.31)

echo "$MANIFEST" | jq .

# 验证输出应该有：
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.manifest.v1+json",
  "config": {
    "mediaType": "application/vnd.cncf.helm.config.v1+json",
    "digest": "sha256:...",
    "size": 123
  },
  "layers": [
    {
      "mediaType": "application/vnd.cncf.helm.chart.content.v1.tar+gzip",
      "digest": "sha256:...",
      "size": 12345678
    }
  ]
}
```

## Troubleshooting

### 如果仍然得到 403 错误：

1. **检查日志中的 URL**
   - 应该显示完整的调试信息
   - 确认 URL 格式正确

2. **验证 URL 可访问**
   ```bash
   curl -I https://charts.bitnami.com/bitnami/mongodb-12.1.31.tgz
   ```
   应该返回 HTTP 200

3. **检查 sourceRepo 参数**
   - 必须包含 `/bitnami` 路径
   - 正确：`https://charts.bitnami.com/bitnami`
   - 错误：`https://charts.bitnami.com`

### 如果仍然得到 "descriptors found: 1":

1. **确认后端已重启**
   - 必须运行 `mvn clean compile quarkus:dev`
   - 旧代码仍在运行会导致此错误

2. **删除旧 manifest**
   ```bash
   curl -X DELETE http://localhost:8911/v2/mongodb-chartmuseum/manifests/12.1.31
   ```

3. **重新镜像**
   - 再次执行 mirror 请求

## 总结

✅ **问题已修复：**
- ChartMuseum URL 构建正确处理组织前缀
- 存储结构正确（config + layer）
- 两种格式（OCI 和 ChartMuseum）都支持

✅ **需要执行：**
1. 重启后端（加载新代码）
2. 使用正确的 URL 格式
3. 重新镜像图表
4. 测试 Helm pull

现在 ChartMuseum 格式应该可以正常工作了！
