<template>
  <div class="tag-detail-container">
    <div class="page-header">
      <h1>Tag Details</h1>
      <el-button @click="goBack">
        <el-icon><Back /></el-icon>
        Back
      </el-button>
    </div>

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
          <el-tag type="info" size="small">
            {{ config.mediaType.split('/').pop() }}
          </el-tag>
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
              <el-tag type="info" size="small">
                {{ layer.mediaType.split('/').pop() }}
              </el-tag>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, DocumentCopy } from '@element-plus/icons-vue'
import { registryApi } from '../api/registry'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const repositoryName = route.params.name
const tagName = route.params.tag

const tagInfo = ref(null)
const config = ref(null)
const layers = ref([])
const loading = ref(false)

const fetchTagDetails = async () => {
  loading.value = true
  try {
    // Fetch manifest info
    const manifestInfo = await registryApi.getManifestInfo(repositoryName, tagName)
    tagInfo.value = {
      ...manifestInfo,
      pullCommand: `docker pull ${window.location.hostname}:${window.location.port || 80}/${repositoryName}:${tagName}`
    }

    // Fetch manifest to get layers
    const manifest = await registryApi.getManifest(repositoryName, tagName)

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

.config-properties {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tag-properties,
.layer-properties {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

</style>
