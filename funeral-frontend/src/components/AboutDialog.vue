<template>
  <el-dialog
    v-model="visible"
    title="About FUNERAL"
    width="500px"
    :close-on-click-modal="true"
    class="about-dialog"
  >
    <div class="about-content">
      <div class="logo-section">
        <img src="/image/funeral.png" alt="FUNERAL Logo" class="about-logo" />
      </div>

      <div class="info-section">
        <h2 class="project-name">FUNERAL</h2>
        <p class="project-description">Open Container Initiative (OCI) Registry</p>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="Version">
            <el-tag type="info">{{ version }}</el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="Project URL">
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
            A lightweight OCI (Open Container Initiative) image registry implemented in Java that follows the OCI Distribution Specification.
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

    <template #footer>
      <el-button @click="visible = false">Close</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import packageInfo from '../../package.json'

const visible = ref(false)
const version = packageInfo.version

const open = () => {
  visible.value = true
}

const close = () => {
  visible.value = false
}

// Expose methods to parent
defineExpose({
  open,
  close
})
</script>

<style scoped>
.about-dialog {
  --el-dialog-border-radius: 12px;
}

.about-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.logo-section {
  display: flex;
  justify-content: center;
  align-items: center;
}

.about-logo {
  width: 120px;
  height: 120px;
  object-fit: contain;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.project-name {
  text-align: center;
  margin: 0;
  color: #303133;
  font-size: 28px;
}

.project-description {
  text-align: center;
  margin: 0;
  color: #606266;
  font-size: 16px;
}

.tech-stack {
  margin-top: 20px;
}

.tech-stack h3 {
  margin: 0 0 12px 0;
  color: #303133;
  font-size: 16px;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tech-tags .el-tag {
  font-weight: normal;
}

:deep(.el-descriptions-item__label) {
  width: 100px;
  text-align: right;
  font-weight: 600;
}
</style>
