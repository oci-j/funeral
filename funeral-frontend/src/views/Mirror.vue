<template>
  <div class="mirror-container">
    <div class="page-header">
      <h1>🏗️ Mirror Image</h1>
      <p class="subtitle">Pull images from external registries directly to this registry</p>
    </div>

    <el-card class="mirror-card">
      <template #header>
        <div class="card-header">
          <span>📦 Pull from External Registry</span>
          <el-tag type="success">New Feature</el-tag>
        </div>
      </template>

      <el-form
        ref="mirrorForm"
        :model="form"
        :rules="rules"
        label-width="140px"
        class="mirror-form"
      >
        <el-form-item label="Source Image" prop="sourceImage">
          <el-input
            v-model="form.sourceImage"
            placeholder="docker.1ms.run/library/nginx:latest"
            clearable
          >
            <template #append>
              <el-tooltip content="Examples: nginx:latest, docker.1ms.run/library/mysql:8.0">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip">Full image reference including registry, repository, and tag</div>
        </el-form-item>

        <el-form-item label="Target Repository" prop="targetRepository">
          <el-input
            v-model="form.targetRepository"
            placeholder="nginx"
            clearable
          />
          <div class="form-tip">Repository name in this registry (defaults to source repo name)</div>
        </el-form-item>

        <el-form-item label="Target Tag" prop="targetTag">
          <el-input
            v-model="form.targetTag"
            placeholder="latest"
            clearable
          />
          <div class="form-tip">Tag for the mirrored image (defaults to source tag)</div>
        </el-form-item>

        <el-form-item label="Protocol">
          <el-select v-model="form.protocol" style="width: 100%">
            <el-option label="HTTPS (Secure)" value="https" />
            <el-option label="HTTP (Insecure)" value="http" />
          </el-select>
          <div class="form-tip">Choose the protocol for connecting to the external registry</div>
        </el-form-item>

        <el-divider content-position="left">Authentication (Optional)</el-divider>

        <el-alert
          title="Leave empty for public images. For private images, provide registry credentials."
          type="info"
          :closable="false"
          show-icon
          class="auth-alert"
        />

        <el-form-item label="Username">
          <el-input
            v-model="form.username"
            placeholder="registry username"
            clearable
          />
        </el-form-item>

        <el-form-item label="Password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="registry password"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="Insecure">
          <el-switch
            v-model="form.insecure"
            active-text="Allow insecure HTTPS"
          />
          <div class="form-tip">Only enable for self-signed certificates</div>
        </el-form-item>
      </el-form>

      <div class="mirror-actions">
        <el-button
          type="primary"
          size="large"
          :loading="mirroring"
          :disabled="!form.sourceImage || mirroring"
          @click="startMirroring"
        >
          <el-icon><Download /></el-icon>
          Start Mirroring
        </el-button>
        <el-button
          @click="resetForm"
          :disabled="mirroring"
        >
          Reset
        </el-button>
      </div>
    </el-card>

    <el-card v-if="result" class="result-card">
      <template #header>
        <div class="card-header">
          <span>📊 Mirror Result</span>
        </div>
      </template>

      <div v-if="result.success" class="result-success">
        <el-result icon="success" title="Mirror Successful">
          <template #subTitle>
            <div class="result-details">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="Source Image">
                  <el-tag type="info">{{ result.sourceImage }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="Target">
                  <el-tag type="success">{{ result.targetRepository }}:{{ result.targetTag }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="Manifest Digest">
                  <el-text tag="code" size="small">{{ result.manifestDigest }}</el-text>
                </el-descriptions-item>
                <el-descriptions-item label="Blobs Pulled">
                  <el-tag type="info">{{ result.blobsCount }}</el-tag>
                </el-descriptions-item>
              </el-descriptions>

              <div class="result-actions">
                <el-button
                  type="primary"
                  @click="goToRepository"
                >
                  View Repository
                </el-button>
                <el-button
                  @click="copyPullCommand"
                >
                  <el-icon><DocumentCopy /></el-icon>
                  Copy Pull Command
                </el-button>
              </div>
            </div>
          </template>
        </el-result>
      </div>

      <div v-else class="result-error">
        <el-result icon="error" title="Mirror Failed">
          <template #subTitle>
            <el-alert
              :title="result.error"
              type="error"
              :closable="false"
              show-icon
            />
          </template>
        </el-result>
      </div>
    </el-card>

    <el-card class="help-card">
      <template #header>
        <div class="card-header">
          <span>💡 Help & Examples</span>
        </div>
      </template>

      <el-collapse>
        <el-collapse-item title="📖 How it works" name="1">
          <div class="help-content">
            <p>1. Enter the source image reference from any OCI-compliant registry</p>
            <p>2. Optional: Customize the target repository name and tag</p>
            <p>3. For private images, provide registry credentials</p>
            <p>4. The service will pull the image and all its layers automatically</p>
          </div>
        </el-collapse-item>

        <el-collapse-item title="📝 Example References" name="2">
          <div class="help-content">
            <el-row :gutter="20">
              <el-col :span="12">
                <h4>Docker Hub (Public)</h4>
                <ul>
                  <li><code>nginx:latest</code></li>
                  <li><code>mysql:8.0</code></li>
                  <li><code>ubuntu:20.04</code></li>
                </ul>
              </el-col>
              <el-col :span="12">
                <h4>Full References</h4>
                <ul>
                  <li><code>docker.io/library/alpine:3.18</code></li>
                  <li><code>gcr.io/google-containers/pause:3.2</code></li>
                  <li><code>quay.io/prometheus/prometheus:latest</code></li>
                </ul>
              </el-col>
            </el-row>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { Download, DocumentCopy, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const router = useRouter()
const authStore = useAuthStore()

const mirroring = ref(false)
const result = ref(null)

const form = reactive({
  sourceImage: '',
  targetRepository: '',
  targetTag: '',
  protocol: 'https', // Default to HTTPS
  username: '',
  password: '',
  insecure: false
})

const rules = {
  sourceImage: [
    { required: true, message: 'Please enter source image reference', trigger: 'blur' }
  ]
}

const startMirroring = async () => {
  if (!form.sourceImage) {
    ElMessage.error('Please enter a source image')
    return
  }

  mirroring.value = true
  result.value = null

  try {
    // Clean the source image - extract from docker pull command if needed
    let cleanedSourceImage = form.sourceImage.trim()
    if (cleanedSourceImage.startsWith('docker pull ')) {
      cleanedSourceImage = cleanedSourceImage.substring('docker pull '.length).trim()
    }

    // Prepare form data
    const formData = new URLSearchParams()
    formData.append('sourceImage', cleanedSourceImage)

    if (form.targetRepository?.trim()) {
      formData.append('targetRepository', form.targetRepository.trim())
    }

    if (form.targetTag?.trim()) {
      formData.append('targetTag', form.targetTag.trim())
    }

    // Add protocol (http or https)
    formData.append('protocol', form.protocol || 'https')

    if (form.username?.trim()) {
      formData.append('username', form.username.trim())
    }

    if (form.password?.trim()) {
      formData.append('password', form.password.trim())
    }

    if (form.insecure) {
      formData.append('insecure', 'true')
    }

    // Get auth headers
    const authHeader = authStore.getAuthHeader()
    const headers = {
      'Content-Type': 'application/x-www-form-urlencoded'
    }

    if (authHeader) {
      headers['Authorization'] = authHeader
    }

    const response = await fetch('/funeral_addition/mirror/pull', {
      method: 'POST',
      headers,
      body: formData,
      credentials: 'include'
    })

    if (response.status === 401) {
      authStore.logout()
      throw new Error('Authentication required. Please log in.')
    }

    if (!response.ok) {
      const errorData = await response.json()
      const errorMsg = errorData.errors?.[0]?.message || `Mirror failed: ${response.statusText}`
      throw new Error(errorMsg)
    }

    const data = await response.json()
    result.value = {
      success: true,
      ...data
    }

    ElMessage.success(`Successfully mirrored ${data.sourceImage}!`)
  } catch (error) {
    console.error('Mirror error:', error)
    result.value = {
      success: false,
      error: error.message || 'Mirror failed'
    }
    ElMessage.error(error.message || 'Mirror failed')
  } finally {
    mirroring.value = false
  }
}

const resetForm = () => {
  form.sourceImage = ''
  form.targetRepository = ''
  form.targetTag = ''
  form.protocol = 'https'
  form.username = ''
  form.password = ''
  form.insecure = false
  result.value = null
}

const goToRepository = () => {
  if (result.value?.targetRepository) {
    router.push(`/repository/${result.value.targetRepository}`)
  }
}

const pullCommand = computed(() => {
  if (!result.value?.targetRepository || !result.value?.targetTag) return ''
  const { hostname, port } = window.location
  const registryUrl = port && port !== '80' ? `${hostname}:${port}` : hostname
  return `docker pull ${registryUrl}/${result.value.targetRepository}:${result.value.targetTag}`
})

const copyPullCommand = async () => {
  if (!pullCommand.value) return
  try {
    await navigator.clipboard.writeText(pullCommand.value)
    ElMessage.success('Pull command copied to clipboard')
  } catch (error) {
    ElMessage.error('Failed to copy to clipboard')
  }
}
</script>

<style scoped>
.mirror-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 30px;
  text-align: center;
}

.page-header h1 {
  margin: 0;
  font-size: 32px;
  color: #303133;
}

.subtitle {
  margin-top: 8px;
  font-size: 16px;
  color: #909399;
}

.mirror-card,
.result-card,
.help-card {
  margin-bottom: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  font-weight: bold;
  font-size: 16px;
}

.mirror-form {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px 0;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.2;
}

.auth-alert {
  margin-bottom: 20px;
}

.mirror-actions {
  text-align: center;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

.result-actions {
  margin-top: 20px;
  text-align: center;
}

.help-content {
  padding: 15px;
}

.help-content p {
  margin: 8px 0;
  line-height: 1.6;
}

.help-content h4 {
  margin: 15px 0 10px 0;
  color: #303133;
}

.help-content ul {
  margin: 5px 0;
  padding-left: 20px;
}

.help-content code {
  background-color: #f4f4f4;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 14px;
}

.result-success,
.result-error {
  padding: 20px 0;
}

.result-details {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

:deep(.el-descriptions-item__label) {
  font-weight: bold;
  width: 160px;
}

:deep(.el-result__icon svg) {
  width: 80px;
  height: 80px;
}

@media (max-width: 768px) {
  .mirror-container {
    padding: 10px;
  }

  .page-header h1 {
    font-size: 24px;
  }

  .mirror-form {
    padding: 10px 0;
  }
}
</style>
