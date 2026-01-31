<template>
  <el-dialog
      v-model="visible"
      width="700px"
      :close-on-click-modal="true"
      :lock-scroll="false"
      align-center
      class="about-dialog centered-dialog"
  >
    <div class="about-content">
      <!-- Center wrapper -->
      <div class="about-content-wrapper">
        <div class="logo-section">
          <img src="/image/funeral.jpg" alt="FUNERAL Logo" class="about-logo"/>
        </div>

        <div class="info-section">
          <h2 class="project-name">FUNERAL</h2>
          <p class="project-description">Open Container Initiative (OCI) Registry</p>

          <el-descriptions :column="1" border class="centered-table">
            <el-descriptions-item label="Version">
              <el-tag type="info">{{ version }}</el-tag>
            </el-descriptions-item>

            <el-descriptions-item label="Sources">
              <el-link
                  type="primary"
                  href="https://github.com/oci-j/funeral.git"
                  target="_blank"
              >
                https://github.com/oci-j/funeral.git
              </el-link>
            </el-descriptions-item>

            <el-descriptions-item label="License">
              <el-tag type="warning">Apache 2.0</el-tag>
            </el-descriptions-item>

            <el-descriptions-item label="Author">
              XenoAmess
            </el-descriptions-item>

            <el-descriptions-item label="Description">
              A lightweight OCI (Open Container Initiative) image registry implemented in Java that follows the OCI
              Distribution Specification.
            </el-descriptions-item>
            <el-descriptions-item label="Sponsor">
              <a href='https://ko-fi.com/P5P11S9YKU' target='_blank'><img height='36' style='border:0px;height:36px;'
                                                                          src='https://storage.ko-fi.com/cdn/kofi5.png?v=6'
                                                                          border='0'
                                                                          alt='Buy Me a Coffee at ko-fi.com'/></a>
            </el-descriptions-item>
            <el-descriptions-item label="Runtime">
              <!-- Runtime Info Section -->
              <div v-if="loadingRuntime" class="runtime-loading">
                <el-text type="info">Loading runtime info...</el-text>
              </div>

              <div v-else-if="runtimeInfo">
                <div class="runtime-info-row">
                  <el-tag :type="runtimeInfo.isNativeImage ? 'success' : 'info'" size="small" class="runtime-tag">
                    {{ runtimeInfo.isNativeImage ? 'Native' : 'JVM' }}
                  </el-tag>
                  <span class="runtime-details">
                    <span v-if="!runtimeInfo.isNativeImage" class="detail-item">Java {{ runtimeInfo.javaVersion }}</span>
                    <span class="detail-item">{{ runtimeInfo.osName }} ({{ runtimeInfo.osArch }})</span>
                    <span class="detail-item">PID:{{ runtimeInfo.pid }}</span>
                  </span>
                  <span v-if="runtimeInfo.canDownload" class="download-section-inline">
                <el-button
                    type="primary"
                    size="small"
                    @click="downloadBinary"
                    :loading="loadingDownload"
                    style="padding: 0 8px; height: 24px;"
                >
                  <el-icon><Download/></el-icon>
                  {{ formatFileSize(runtimeInfo.binarySize) }}
                </el-button>
              </span>
                </div>
                <div class="runtime-note" v-if="runtimeInfo.binaryName">
                  {{ runtimeInfo.binaryName }}
                </div>
              </div>
            </el-descriptions-item>
          </el-descriptions>

          <div class="tech-stack">
            <h3>Technology Stack</h3>
            <div class="tech-tags">
              <el-tag size="small">Java/GraalVm</el-tag>
              <el-tag size="small" type="success">Quarkus</el-tag>
              <el-tag size="small" type="info">Vue.js 3</el-tag>
              <el-tag size="small" type="warning">Element Plus</el-tag>
              <el-tag size="small" type="danger">MongoDB</el-tag>
              <el-tag size="small" type="info">MinIO</el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import {ref} from 'vue'
import packageInfo from '../../package.json'

const visible = ref(false)
const version = packageInfo.version
const runtimeInfo = ref(null)
const loadingRuntime = ref(false)
const loadingDownload = ref(false)

const open = async () => {
  visible.value = true
  await fetchRuntimeInfo()
}

const close = () => {
  visible.value = false
}

const fetchRuntimeInfo = async () => {
  loadingRuntime.value = true
  try {
    const response = await fetch('/funeral_addition/config/runtime')
    if (response.ok) {
      runtimeInfo.value = await response.json()
    }
  } catch (error) {
    console.error('Failed to fetch runtime info:', error)
  } finally {
    loadingRuntime.value = false
  }
}

const downloadBinary = async () => {
  if (!runtimeInfo.value?.canDownload) return

  loadingDownload.value = true
  try {
    const response = await fetch('/funeral_addition/config/download/binary')
    if (!response.ok) {
      throw new Error('Download failed')
    }

    // Get filename from headers
    const contentDisposition = response.headers.get('content-disposition')
    const filename = contentDisposition
        ? contentDisposition.split('filename="')[1]?.split('"')[0]
        : 'funeral-binary'

    // Create blob and download
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('Download failed:', error)
    alert('Failed to download binary: ' + error.message)
  } finally {
    loadingDownload.value = false
  }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// Expose methods to parent
defineExpose({
  open,
  close
})
</script>

<style>
/* Remove scoped to ensure styles are applied */

/* Center the dialog vertically and horizontally */
.centered-dialog {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.centered-dialog .el-dialog {
  margin: 0 !important;
}

.about-dialog {
  --el-dialog-border-radius: 12px;
}

/* Center the dialog title - with higher specificity */
.about-dialog .el-dialog__header {
  display: none;
}

/* Simple centering - remove flex to avoid issues */
.about-content {
  padding: 20px;
}

.about-content-wrapper {
  max-width: 600px;
  margin: 0 auto;
}

/* Logo styling */
.logo-section {
  display: flex;
  justify-content: center;
  align-items: center;
}

.about-logo {
  width: 250px;
  height: 250px;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* Info section with consistent spacing */
.info-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Project name and description */
.project-name {
  text-align: center;
  margin: 0;
  color: #303133;
  font-size: 32px;
  font-weight: bold;
}

.project-description {
  text-align: center;
  margin: 0;
  color: #606266;
  font-size: 18px;
  letter-spacing: 0.5px;
}

/* Center the descriptions table */
.about-content .el-descriptions {
  width: 100% !important;
  max-width: 600px !important;
  margin: 0 auto !important;
}

:global(.about-content .el-descriptions .el-descriptions__body) {
  margin: 0 auto !important;
}

:global(.about-content .el-descriptions table) {
  margin: 0 auto !important;
}

.about-dialog .el-descriptions-item__label {
  width: 120px;
  text-align: right;
  font-weight: 600;
}

/* Tech stack section */
.tech-stack {
  margin-top: 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tech-stack h3 {
  margin: 0 0 20px 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 600px;
}

.tech-tags :deep(.el-tag) {
  font-weight: normal;
}

/* Runtime info styles - Compact one-line display */
.runtime-loading {
  text-align: center;
  padding: 20px;
}

.runtime-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

.runtime-section h3 {
  text-align: center;
  margin-bottom: 12px;
  color: #303133;
}

.runtime-info-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 13px;
  flex-wrap: wrap;
}

.runtime-tag {
  flex-shrink: 0;
}

.runtime-details {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #606266;
}

.detail-item:not(:last-child)::after {
  content: "·";
  margin-left: 12px;
  color: #dcdfe6;
}

.download-section-inline {
  display: flex;
  align-items: center;
}

.runtime-note {
  margin-top: 8px;
  text-align: center;
  font-family: monospace;
  font-size: 11px;
  color: #909399;
  word-break: break-all;
}

/* Mobile styles */
@media (max-width: 768px) {
  .runtime-section h3 {
    font-size: 16px;
  }
}

</style>
