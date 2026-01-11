<template>
  <div class="tag-detail-page">
    <div class="page-header">
      <h1>Tag Details</h1>
      <el-button @click="goBack">
        <el-icon><Back /></el-icon>
        Back
      </el-button>
    </div>

    <div class="tag-detail-container">

      <el-card v-if="tagInfo" class="tag-info-card">
        <template #header>
          <div class="card-header">
            <span>{{ repositoryName }}:{{ tagName }}</span>
          </div>
        </template>
        <div class="tag-properties">
          <div class="property-item">
            <el-text type="info">Digest:</el-text>
            <el-text class="digest-text">{{ tagInfo.digest }}</el-text>
          </div>
          <div class="property-item">
            <el-text type="info">Size:</el-text>
            <el-text>{{ formatSize(tagInfo.size) }}</el-text>
          </div>
          <div class="property-item">
            <el-text type="info">Created:</el-text>
            <el-text>{{ tagInfo.created || 'Unknown' }}</el-text>
          </div>
          <div class="property-item">
            <el-text type="info">Media Type:</el-text>
            <el-text>{{ tagInfo.mediaType || 'Unknown' }}</el-text>
          </div>
        </div>

        <div v-if="tagInfo.pullCommand" class="pull-command">
          <el-text type="info">Pull Command:</el-text>
          <el-input :model-value="tagInfo.pullCommand" readonly class="command-input">
            <template #append>
              <el-button @click="copyToClipboard(tagInfo.pullCommand)">
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
            </template>
          </el-input>
        </div>
      </el-card>

      <el-card v-if="config" class="config-card">
        <template #header>
          <div class="card-header">
            <span>Configuration</span>
            <div class="header-actions">
              <el-tag type="info" size="small">
                {{ config.mediaType.split('/').pop() }}
              </el-tag>
              <el-button type="primary" size="small" @click="showBlobContent(config)">
                <el-icon><Document /></el-icon>
                Details
              </el-button>
            </div>
          </div>
        </template>
        <div class="config-properties">
          <div class="property-item">
            <el-text type="info">Digest:</el-text>
            <el-text class="digest-text">{{ config.digest }}</el-text>
          </div>
          <div class="property-item">
            <el-text type="info">Size:</el-text>
            <el-text>{{ formatSize(config.size) }}</el-text>
          </div>
        </div>
      </el-card>

      <el-card v-if="layers.length > 0" class="layers-card">
        <template #header>
          <div class="card-header">
            <span>Layers ({{ layers.length }})</span>
          </div>
        </template>
        <div class="layers-list">
          <el-card v-for="(layer, index) in layers" :key="layer.digest" class="layer-card">
            <template #header>
              <div class="layer-header">
                <span class="layer-title">Layer {{ index + 1 }}</span>
                <div class="header-actions">
                  <el-tag type="info" size="small">
                    {{ layer.mediaType.split('/').pop() }}
                  </el-tag>
                  <el-button type="primary" size="small" @click="showBlobContent(layer)">
                    <el-icon><Document /></el-icon>
                    Details
                  </el-button>
                </div>
              </div>
            </template>
            <div class="layer-properties">
              <div class="property-item">
                <el-text type="info">Digest:</el-text>
                <el-text class="digest-text">{{ layer.digest }}</el-text>
              </div>
              <div class="property-item">
                <el-text type="info">Size:</el-text>
                <el-text>{{ formatSize(layer.size) }}</el-text>
              </div>
            </div>
          </el-card>
        </div>
      </el-card>

      <el-empty v-if="!loading && !tagInfo" description="Tag not found" />

      <!-- Blob Content Dialog -->
      <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="80%"
        top="5vh"
        :close-on-click-modal="false"
      >
        <div class="blob-content-container">
          <div v-if="dialogLoading" class="loading-container">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>Loading content...</span>
          </div>
          <div v-else-if="dialogError" class="error-container">
            <el-alert :title="dialogError" type="error" :closable="false" />
          </div>
          <div v-else-if="blobContent" class="content-viewer">
            <el-alert
              v-if="blobContent.type === 'blob'"
              title="This is a binary blob. Content preview is not available."
              type="info"
              :closable="false"
            />
            <div v-if="vueJsonPrettyAvailable && isJsonContent && jsonData" class="json-pretty-container">
              <vue-json-pretty
                :data="jsonData"
                :deep="3"
                :show-length="true"
                :show-line="true"
                :show-double-quotes="true"
              />
            </div>
            <pre v-else-if="isJsonContent && jsonData" class="json-viewer">{{ JSON.stringify(jsonData, null, 2) }}</pre>
            <pre v-else class="text-viewer">{{ blobContent.content }}</pre>
          </div>
        </div>
        <template #footer>
          <el-button @click="dialogVisible = false">Close</el-button>
          <el-button
            v-if="blobContent && blobContent.type === 'text'"
            type="primary"
            @click="copyToClipboard(blobContent.content)"
          >
            Copy Content
          </el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, DocumentCopy, Document, Loading } from '@element-plus/icons-vue'

import { registryApi } from '../api/registry'
import { ElMessage } from 'element-plus'

// Define props
const props = defineProps({
  name: {
    type: String,
    required: true
  },
  tag: {
    type: String,
    required: true
  }
})

// Check if vue-json-pretty is available
let VueJsonPretty = null
let vueJsonPrettyAvailable = false

// Load vue-json-pretty asynchronously without blocking setup
const loadVueJsonPretty = async () => {
  try {
    const module = await import('vue-json-pretty')
    VueJsonPretty = module.default
    await import('vue-json-pretty/lib/styles.css')
    vueJsonPrettyAvailable = true
    console.log('vue-json-pretty loaded successfully')
  } catch (e) {
    console.warn('vue-json-pretty not available, using fallback JSON display')
  }
}

const route = useRoute()
const router = useRouter()

// Use props instead of route.params
const repositoryName = ref(props.name)
const tagName = ref(props.tag)

// Watch for props changes
watch(() => props.name, (newName) => {
  repositoryName.value = newName
  fetchTagDetails()
})

watch(() => props.tag, (newTag) => {
  tagName.value = newTag
  fetchTagDetails()
})

const tagInfo = ref(null)
const config = ref(null)
const layers = ref([])
const loading = ref(false)

// Dialog state
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogLoading = ref(false)
const dialogError = ref('')
const blobContent = ref(null)
const currentBlob = ref(null)

// Prevent attribute inheritance
defineOptions({
  inheritAttrs: false
})

const fetchTagDetails = async () => {
  loading.value = true
  try {
    // Fetch manifest info
    const manifestInfo = await registryApi.getManifestInfo(repositoryName.value, tagName.value)
    tagInfo.value = {
      ...manifestInfo,
      pullCommand: `docker pull ${window.location.hostname}:${window.location.port || 80}/${repositoryName.value}:${tagName.value}`
    }

    // Fetch manifest to get layers
    const manifest = await registryApi.getManifest(repositoryName.value, tagName.value)

    // Process config separately (not part of layers anymore)
    if (manifest.config) {
      config.value = {
        digest: manifest.config.digest,
        size: manifest.config.size,
        mediaType: manifest.config.mediaType || 'application/vnd.oci.image.config.v1+json'
      }
    }

    // Process layers in the exact order from manifest
    const layerList = []

    // Add layers from manifest layers array only
    if (manifest.layers) {
      const layersFromManifest = manifest.layers.map(layer => ({
        digest: layer.digest,
        size: layer.size,
        mediaType: layer.mediaType
      }))
      layerList.push(...layersFromManifest)
    }

    layers.value = layerList
  } catch (error) {
    ElMessage.error(`Failed to fetch tag details: ${error.message}`)
    console.error('Error fetching tag details:', error)
  } finally {
    loading.value = false
  }
}

const formatSize = (size) => {
  if (size === 'Unknown' || size === null || size === undefined) return 'Unknown'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

const showBlobContent = async (blob) => {
  currentBlob.value = blob
  dialogTitle.value = `Content: ${blob.digest.substring(0, 16)}...`
  dialogVisible.value = true
  dialogLoading.value = true
  dialogError.value = ''
  blobContent.value = null

  try {
    const result = await registryApi.getBlobContent(repositoryName.value, blob.digest, blob.mediaType || '')
    blobContent.value = result
  } catch (error) {
    dialogError.value = `Failed to fetch blob content: ${error.message}`
    console.error('Error fetching blob content:', error)
  } finally {
    dialogLoading.value = false
  }
}

const isJsonContent = computed(() => {
  if (!blobContent.value || blobContent.value.type !== 'text') return false
  const contentType = blobContent.value.contentType.toLowerCase()
  const mediaType = (blobContent.value.mediaType || '').toLowerCase()
  return contentType.includes('application/json') ||
         contentType.includes('+json') ||
         mediaType.includes('+json')
})

const jsonData = computed(() => {
  if (!blobContent.value || blobContent.value.type !== 'text') return null
  try {
    return JSON.parse(blobContent.value.content)
  } catch (e) {
    return null
  }
})

const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('Copied to clipboard')
  } catch (error) {
    ElMessage.error('Failed to copy to clipboard')
  }
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  // Load vue-json-pretty asynchronously without blocking
  loadVueJsonPretty()

  fetchTagDetails()
})

</script>

<style scoped>
.tag-detail-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  color: #409EFF;
}

.tag-info-card {
  margin-bottom: 20px;
}

.config-card {
  margin-bottom: 20px;
}

.config-properties,
.layer-properties {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.layer-properties {
  padding: 16px 0 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.layer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.layer-title {
  font-weight: bold;
}

.property-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.digest-text {
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
}

.pull-command {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

.command-input {
  flex: 1;
}

.layers-card {
  margin-bottom: 20px;
}

.layers-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.layer-card {
  position: relative;
}

.layer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.layer-title {
  font-weight: bold;
}

/* Json Pretty Container */
.json-pretty-container {
  padding: 10px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: auto;
  max-height: 60vh;
}

:deep(.vjs-tree) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
}

:deep(.vjs-tree-node) {
  padding: 2px 0;
}

:deep(.vjs-key) {
  color: #0969da;
}

:deep(.vjs-value) {
  color: #cf222e;
}

:deep(.vjs-value-string) {
  color: #0a3069;
}

:deep(.vjs-value-number) {
  color: #0550ae;
}

:deep(.vjs-value-boolean) {
  color: #cf222e;
  font-weight: bold;
}

:deep(.vjs-value-null) {
  color: #656d76;
  font-style: italic;
}

:deep(.vjs-tree__brackets) {
  color: #656d76;
  cursor: pointer;
}

:deep(.vjs-tree__brackets:hover) {
  color: #0969da;
}

:deep(.vjs-check-controller) {
  color: #656d76;
}

:deep(.vjs-check-controller:hover) {
  color: #0969da;
}

:deep(.vjs-tree__length) {
  color: #656d76;
  font-size: 12px;
}

/* Fallback JSON viewer when vue-json-pretty is not available */
.json-viewer {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  padding: 15px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* Dialog styles */
.blob-content-container {
  min-height: 400px;
  max-height: 70vh;
  overflow: auto;
}

.loading-container,
.error-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  gap: 8px;
}

.content-viewer {
  padding: 10px;
}

.text-viewer {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  padding: 15px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* Add scrollbar styles for content viewer */
.json-viewer::-webkit-scrollbar,
.text-viewer::-webkit-scrollbar {
  height: 8px;
  width: 8px;
}

.json-viewer::-webkit-scrollbar-track,
.text-viewer::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.json-viewer::-webkit-scrollbar-thumb,
.text-viewer::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.json-viewer::-webkit-scrollbar-thumb:hover,
.text-viewer::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

</style>
