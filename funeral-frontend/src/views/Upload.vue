<template>
  <div class="upload-container">
    <div class="page-header">
      <h1>Upload Image</h1>
    </div>

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
import { DocumentCopy } from '@element-plus/icons-vue'

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
</style>
