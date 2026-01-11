<template>
  <div class="upload-container">
    <div class="page-header">
      <h1>Upload Image</h1>
    </div>

    <!-- Docker Tar Upload Section -->
    <el-card class="upload-tar-card">
      <template #header>
        <div class="card-header">
          <span>📦 Upload Docker Tar File</span>
          <el-tag type="success">New Feature</el-tag>
        </div>
      </template>

      <div class="upload-section">
        <el-upload
          ref="uploadRef"
          v-model:file-list="fileList"
          class="tar-uploader"
          drag
          action="#"
          accept=".tar,.tar.gz,.tgz"
          :auto-upload="false"
          :before-upload="handleBeforeUpload"
          :on-remove="handleFileRemove"
          multiple
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            Drop Docker tar file here or <em>click to upload</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              Use <code>docker save image:tag -o image.tar</code> to create tar file
            </div>
          </template>
        </el-upload>

        <div class="upload-actions">
          <el-button
            type="primary"
            :loading="uploading"
            :disabled="fileList.length === 0"
            @click="uploadTarFiles"
          >
            Upload and Analyze
          </el-button>
        </div>

        <div v-if="uploadResult" class="upload-result">
          <el-alert
            :title="uploadResult.success ? 'Upload successful!' : 'Upload failed'"
            :type="uploadResult.success ? 'success' : 'error'"
            :closable="false"
            show-icon
          />

          <div v-if="uploadResult.repositories" class="result-details">
            <h4>📊 Analysis Results:</h4>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="Repositories Found">
                <el-tag
                  v-for="repo in uploadResult.repositories"
                  :key="repo"
                  type="info"
                  class="result-tag"
                >
                  {{ repo }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="Manifests">
                {{ uploadResult.manifests?.length || 0 }}
              </el-descriptions-item>
              <el-descriptions-item label="Blobs">
                {{ uploadResult.blobs?.length || 0 }}
              </el-descriptions-item>
            </el-descriptions>

            <div v-if="uploadResult.manifests" class="manifest-list">
              <h4>📋 Image Tags Found:</h4>
              <el-table :data="uploadResult.manifests" border stripe style="width: 100%">
                <el-table-column prop="repository" label="Repository" />
                <el-table-column prop="tag" label="Tag" />
                <el-table-column prop="configDigest" label="Config Digest" show-overflow-tooltip />
                <el-table-column label="Layers" width="100">
                  <template #default="scope">
                    <el-tag size="small">{{ scope.row.layerDigests?.length || 0 }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-divider content-position="center">OR</el-divider>

    <!-- Docker Push Guide Section -->
    <el-card class="upload-card">
      <template #header>
        <div class="card-header">
          <span>Docker Push Guide</span>
        </div>
      </template>

      <div class="upload-content">
        <div class="step">
          <h3>Step 1: Tag your image</h3>
          <div class="step-content">
            <el-text type="info">Use the following command to tag your Docker image:</el-text>
            <el-input
              v-model="tagCommand"
              readonly
              class="command-input"
            >
              <template #append>
                <el-button @click="copyToClipboard(tagCommand)">
                  <el-icon><DocumentCopy /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
        </div>

        <div class="step">
          <h3>Step 2: Push to registry</h3>
          <div class="step-content">
            <el-text type="info">Then push the tagged image to this registry:</el-text>
            <el-input
              v-model="pushCommand"
              readonly
              class="command-input"
            >
              <template #append>
                <el-button @click="copyToClipboard(pushCommand)">
                  <el-icon><DocumentCopy /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
        </div>

        <div class="step">
          <h3>Step 3: Authenticate (Optional)</h3>
          <div class="step-content">
            <el-text type="info">If authentication is required, login first:</el-text>
            <el-input
              v-model="loginCommand"
              readonly
              class="command-input"
            >
              <template #append>
                <el-button @click="copyToClipboard(loginCommand)">
                  <el-icon><DocumentCopy /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span>Configuration</span>
        </div>
      </template>

      <el-form label-width="120px">
        <el-form-item label="Repository Name">
          <el-input v-model="repositoryName" placeholder="my-app" />
        </el-form-item>

        <el-form-item label="Image Tag">
          <el-input v-model="imageTag" placeholder="latest" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { DocumentCopy, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

// Tar upload state
const uploadRef = ref()
const fileList = ref([])
const uploading = ref(false)
const uploadResult = ref(null)

// Docker push guide state
const repositoryName = ref('my-app')
const imageTag = ref('latest')

const registryUrl = computed(() => {
  const { hostname, port } = window.location
  return port && port !== '80' ? `${hostname}:${port}` : hostname
})

const tagCommand = computed(() => {
  return `docker tag ${repositoryName.value}:${imageTag.value} ${registryUrl.value}/${repositoryName.value}:${imageTag.value}`
})

const pushCommand = computed(() => {
  return `docker push ${registryUrl.value}/${repositoryName.value}:${imageTag.value}`
})

const loginCommand = computed(() => {
  return `docker login ${registryUrl.value}`
})

// Tar file upload handlers
const handleBeforeUpload = (file) => {
  const isTar = file.name.endsWith('.tar') || file.name.endsWith('.tar.gz') || file.name.endsWith('.tgz')
  if (!isTar) {
    ElMessage.error('Only .tar, .tar.gz, and .tgz files are allowed')
    return false
  }
  return true
}

const handleFileRemove = () => {
  uploadResult.value = null
}

const uploadTarFiles = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('Please select at least one file')
    return
  }

  uploading.value = true
  uploadResult.value = null

  try {
    const formData = new FormData()
    fileList.value.forEach((file) => {
      formData.append('file', file.raw)
    })

    // Get auth headers from auth store
    const authStore = useAuthStore()
    const authHeader = authStore.getAuthHeader()
    const headers = {
      // Don't set Content-Type for FormData - browser will set it with boundary
    }

    if (authHeader) {
      headers['Authorization'] = authHeader
    }

    const response = await fetch('/api/admin/upload/dockertar', {
      method: 'POST',
      headers,
      body: formData,
      credentials: 'include' // Include cookies for authentication
    })

    if (response.status === 401) {
      authStore.logout()
      throw new Error('Authentication required. Please log in.')
    }

    if (!response.ok) {
      throw new Error(`Upload failed: ${response.statusText}`)
    }

    const result = await response.json()
    uploadResult.value = {
      success: true,
      ...result
    }

    // Clear file list after successful upload
    fileList.value = []

    ElMessage.success('Docker tar file uploaded and analyzed successfully!')

  } catch (error) {
    console.error('Upload error:', error)
    uploadResult.value = {
      success: false,
      error: error.message
    }
    ElMessage.error(error.message || 'Upload failed')
  } finally {
    uploading.value = false
  }
}

// Copy to clipboard
const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('Copied to clipboard')
  } catch (error) {
    ElMessage.error('Failed to copy to clipboard')
  }
}
</script>

<style scoped>
.upload-container {
  padding: 20px;
  max-width: 1200px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
}

.upload-tar-card,
.upload-card,
.config-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 10px;
}

.upload-section {
  padding: 20px 0;
}

.tar-uploader {
  width: 100%;
}

.upload-actions {
  margin-top: 20px;
  text-align: center;
}

.upload-result {
  margin-top: 20px;
}

.result-details {
  margin-top: 20px;
}

.result-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.manifest-list {
  margin-top: 20px;
}

.upload-content {
  padding: 20px 0;
}

.step {
  margin-bottom: 30px;
}

.step:last-child {
  margin-bottom: 0;
}

.step h3 {
  margin-bottom: 10px;
  color: #409EFF;
}

.step-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.command-input {
  font-family: monospace;
  background-color: #f5f7fa;
}

.code {
  background-color: #f5f7fa;
  padding: 2px 4px;
  border-radius: 4px;
  font-family: monospace;
}

:deep(.el-descriptions-item__label) {
  font-weight: bold;
  width: 200px;
}
</style>

<style scoped>
.upload-container {
  padding: 20px;
  max-width: 800px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
}

.upload-card {
  margin-bottom: 20px;
}

.config-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: bold;
}

.step {
  margin-bottom: 30px;
}

.step:last-child {
  margin-bottom: 0;
}

.step h3 {
  margin-bottom: 10px;
  color: #409EFF;
}

.step-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.command-input {
  font-family: monospace;
}

.divider-section {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 30px 0;
}

.divider-line {
  flex: 1;
  height: 1px;
  background-color: #dcdfe6;
}

.divider-text {
  padding: 0 20px;
  color: #909399;
  font-weight: bold;
  font-size: 16px;
}
</style>
