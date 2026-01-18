<template>
  <div class="mirror-helm-container">
    <div class="page-header">
      <h1>🎵 Mirror Helm Chart</h1>
      <p class="subtitle">Pull Helm charts from external repositories directly to this registry</p>
    </div>

    <el-card class="mirror-helm-card">
      <template #header>
        <div class="card-header">
          <span>📦 Pull Helm Chart from External Repository</span>
          <el-tag type="success">New Feature</el-tag>
        </div>
      </template>

      <el-form
        ref="mirrorHelmForm"
        :model="form"
        :rules="rules"
        label-width="160px"
        class="mirror-helm-form"
      >
        <el-form-item label="Repository Format" prop="format">
          <el-select v-model="form.format" style="width: 100%" @change="onFormatChange">
            <el-option label="OCI Registry" value="oci">
              <template #default>
                <div style="display: flex; align-items: center;">
                  <img src="/oci-icon.png" alt="OCI" style="width: 16px; height: 16px; margin-right: 8px;" />
                  OCI Registry (Recommended)
                </div>
              </template>
            </el-option>
            <el-option label="ChartMuseum" value="chartmuseum">
              <template #default>
                <div style="display: flex; align-items: center;">
                  <img src="/helm-icon.png" alt="Helm" style="width: 16px; height: 16px; margin-right: 8px;" />
                  ChartMuseum (Traditional)
                </div>
              </template>
            </el-option>
          </el-select>
          <div class="form-tip">
            <span v-if="form.format === 'oci'">OCI format stores charts as OCI artifacts</span>
            <span v-else>ChartMuseum format uses HTTP API for chart management</span>
          </div>
        </el-form-item>

        <el-form-item label="Source Repository" prop="sourceRepo">
          <el-input
            v-model="form.sourceRepo"
            :placeholder="sourceRepoPlaceholder"
            clearable
          >
            <template #append>
              <el-tooltip :content="sourceRepoTooltip" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip">{{ sourceRepoTip }}</div>
        </el-form-item>

        <el-form-item label="Chart Name" prop="chartName">
          <el-input
            v-model="form.chartName"
            placeholder="nginx"
            clearable
          >
            <template #append>
              <el-tooltip content="e.g., nginx, mysql, prometheus">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip">Name of the Helm chart to pull</div>
        </el-form-item>

        <el-form-item label="Chart Version" prop="version">
          <el-input
            v-model="form.version"
            placeholder="latest"
            clearable
          >
            <template #append>
              <el-tooltip content="Chart version or 'latest'">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip">Version of the chart (e.g., 1.2.3)</div>
        </el-form-item>

        <el-form-item label="Target Repository" prop="targetRepository">
          <el-input
            v-model="form.targetRepository"
            :placeholder="form.chartName || 'my-chart'"
            clearable
          >
            <template #append>
              <el-tooltip content="Repository name in this registry">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip">Repository name in this registry (defaults to chart name)</div>
        </el-form-item>

        <el-form-item label="Target Version" prop="targetVersion">
          <el-input
            v-model="form.targetVersion"
            :placeholder="form.version || 'latest'"
            clearable
          >
            <template #append>
              <el-tooltip content="Version to use in this registry">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip">Version for the mirrored chart (defaults to source version)</div>
        </el-form-item>

        <el-divider content-position="left">Authentication (Optional)</el-divider>

        <el-alert
          title="Leave empty for public repositories. For private repositories, provide credentials."
          type="info"
          :closable="false"
          show-icon
          class="auth-alert"
        />

        <el-form-item label="Username">
          <el-input
            v-model="form.username"
            placeholder="repository username"
            clearable
          />
        </el-form-item>

        <el-form-item label="Password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="repository password"
            show-password
            clearable
          />
        </el-form-item>
      </el-form>

      <div class="mirror-helm-actions">
        <el-button
          type="primary"
          size="large"
          :loading="mirroring"
          :disabled="!form.chartName || mirroring"
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
                <el-descriptions-item label="Source">
                  <el-tag type="info">{{ result.source }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="Chart">
                  <el-tag type="success">{{ result.chart }}:{{ result.version }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="Target">
                  <el-tag type="success">{{ result.targetChart }}:{{ result.targetVersion }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="Format">
                  <el-tag type="info">{{ result.format === 'oci' ? 'OCI' : 'ChartMuseum' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="Digest">
                  <el-text tag="code" size="small">{{ result.digest }}</el-text>
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
                  Copy Helm Install Command
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

      <el-tabs type="border-card">
        <el-tab-pane label="OCI Repository">
          <h4>📦 OCI Registry Examples</h4>
          <p>OCI format stores Helm charts as OCI artifacts in container registries.</p>
          <el-descriptions :column="1" border class="example-table">
            <el-descriptions-item label="Docker Hub">
              <code>registry-1.docker.io</code> or <code>docker.io</code>
            </el-descriptions-item>
            <el-descriptions-item label="Azure Container Registry">
              <code>myregistry.azurecr.io</code>
            </el-descriptions-item>
            <el-descriptions-item label="AWS ECR">
              <code>account-id.dkr.ecr.region.amazonaws.com</code>
            </el-descriptions-item>
            <el-descriptions-item label="Private Registry">
              <code>registry.example.com</code>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            title="OCI format uses the pattern: &lt;registry&gt;/&lt;chart&gt;:&lt;version&gt;"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 10px;"
          />
        </el-tab-pane>

        <el-tab-pane label="ChartMuseum">
          <h4>🎫 ChartMuseum Examples</h4>
          <p>ChartMuseum provides traditional HTTP API for Helm chart management.</p>
          <el-descriptions :column="1" border class="example-table">
            <el-descriptions-item label="Public Repository">
              <code>https://charts.helm.sh/stable</code>
            </el-descriptions-item>
            <el-descriptions-item label="Harbor ChartMuseum">
              <code>https://harbor.example.com/chartrepo/library</code>
            </el-descriptions-item>
            <el-descriptions-item label="JFrog Artifactory">
              <code>https://artifactory.example.com/artifactory/helm-local</code>
            </el-descriptions-item>
            <el-descriptions-item label="Private Server">
              <code>https://charts.example.com</code>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            title="ChartMuseum API endpoints typically end with /charts/&lt;name&gt;-&lt;version&gt;.tgz"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 10px;"
          />
        </el-tab-pane>
      </el-tabs>

      <el-collapse style="margin-top: 20px;">
        <el-collapse-item title="📖 How it works" name="1">
          <div class="help-content">
            <p>1. Choose the repository format (OCI or ChartMuseum)</p>
            <p>2. Enter the source repository URL and chart details</p>
            <p>3. Optional: Customize the target repository name and version</p>
            <p>4. For private repositories, provide credentials</p>
            <p>5. The service will pull the chart and store it in this registry</p>
          </div>
        </el-collapse-item>

        <el-collapse-item title="📝 Popular Charts" name="2">
          <div class="help-content">
            <el-row :gutter="20">
              <el-col :span="12">
                <h4>Web Applications</h4>
                <ul>
                  <li><code>nginx</code> - Web server</li>
                  <li><code>wordpress</code> - CMS platform</li>
                  <li><code>drupal</code> - CMS platform</li>
                </ul>
              </el-col>
              <el-col :span="12">
                <h4>Databases</h4>
                <ul>
                  <li><code>postgresql</code> - PostgreSQL</li>
                  <li><code>mysql</code> - MySQL</li>
                  <li><code>mongodb</code> - MongoDB</li>
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
  format: 'oci',
  sourceRepo: '',
  chartName: '',
  version: '',
  targetRepository: '',
  targetVersion: '',
  username: '',
  password: ''
})

const rules = {
  sourceRepo: [
    { required: true, message: 'Please enter source repository', trigger: 'blur' }
  ],
  chartName: [
    { required: true, message: 'Please enter chart name', trigger: 'blur' }
  ],
  format: [
    { required: true, message: 'Please select repository format', trigger: 'change' }
  ]
}

const sourceRepoPlaceholder = computed(() => {
  return form.format === 'oci'
    ? 'registry-1.docker.io or myregistry.azurecr.io'
    : 'https://charts.helm.sh/stable'
})

const sourceRepoTooltip = computed(() => {
  return form.format === 'oci'
    ? 'OCI registry hostname (e.g., docker.io, myregistry.azurecr.io)'
    : 'ChartMuseum repository URL (e.g., https://charts.example.com)'
})

const sourceRepoTip = computed(() => {
  return form.format === 'oci'
    ? 'OCI registry hostname without protocol'
    : 'Full URL with protocol for ChartMuseum'
})

const onFormatChange = () => {
  // Clear source repo when format changes
  form.sourceRepo = ''
}

const startMirroring = async () => {
  if (!form.chartName || !form.sourceRepo) {
    ElMessage.error('Please complete required fields')
    return
  }

  mirroring.value = true
  result.value = null

  try {
    // Prepare form data
    const formData = new URLSearchParams()
    formData.append('sourceRepo', form.sourceRepo.trim())
    formData.append('chartName', form.chartName.trim())

    if (form.version?.trim()) {
      formData.append('version', form.version.trim())
    }

    if (form.targetRepository?.trim()) {
      formData.append('targetRepository', form.targetRepository.trim())
    } else {
      formData.append('targetRepository', form.chartName.trim())
    }

    if (form.targetVersion?.trim()) {
      formData.append('targetVersion', form.targetVersion.trim())
    } else if (form.version?.trim()) {
      formData.append('targetVersion', form.version.trim())
    }

    formData.append('format', form.format || 'oci')

    if (form.username?.trim()) {
      formData.append('username', form.username.trim())
    }

    if (form.password?.trim()) {
      formData.append('password', form.password.trim())
    }

    // Get auth headers
    const authHeader = authStore.getAuthHeader()
    const headers = {
      'Content-Type': 'application/x-www-form-urlencoded'
    }

    if (authHeader) {
      headers['Authorization'] = authHeader
    }

    const response = await fetch('/funeral_addition/mirror/helm/pull', {
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

    ElMessage.success(`Successfully mirrored ${data.chart || form.chartName}!`)
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
  form.sourceRepo = ''
  form.chartName = ''
  form.version = ''
  form.targetRepository = ''
  form.targetVersion = ''
  form.username = ''
  form.password = ''
  form.format = 'oci'
  result.value = null
}

const goToRepository = () => {
  if (result.value?.targetChart) {
    router.push(`/repository/${result.value.targetChart}`)
  }
}

const helmCommand = computed(() => {
  if (!result.value?.targetChart || !result.value?.targetVersion) return ''
  const { hostname, port } = window.location
  const registryUrl = port && port !== '80' ? `${hostname}:${port}` : hostname

  if (result.value.format === 'oci') {
    return `helm pull oci://${registryUrl}/${result.value.targetChart} --version ${result.value.targetVersion}`
  } else {
    return `helm repo add myrepo http://${registryUrl} && helm repo update && helm install myrelease ${result.value.targetChart} --version ${result.value.targetVersion}`
  }
})

const copyPullCommand = async () => {
  if (!helmCommand.value) return
  try {
    await navigator.clipboard.writeText(helmCommand.value)
    ElMessage.success('Helm command copied to clipboard')
  } catch (error) {
    ElMessage.error('Failed to copy to clipboard')
  }
}
</script>

<style scoped>
.mirror-helm-container {
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

.mirror-helm-card,
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

.mirror-helm-form {
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

.mirror-helm-actions {
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

.example-table {
  margin: 15px 0;
}

:deep(.el-descriptions-item__label) {
  font-weight: bold;
  width: 180px;
}

:deep(.el-result__icon svg) {
  width: 80px;
  height: 80px;
}

@media (max-width: 768px) {
  .mirror-helm-container {
    padding: 10px;
  }

  .page-header h1 {
    font-size: 24px;
  }

  .mirror-helm-form {
    padding: 10px 0;
  }
}
</style>
