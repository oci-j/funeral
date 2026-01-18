<template>
  <div class="repository-container">
    <div class="page-header">
      <h1>{{ repositoryName }}</h1>
      <el-button @click="goBack">
        <el-icon><Back /></el-icon>
        Back
      </el-button>
    </div>

    <el-card class="tag-card" v-for="tag in tags" :key="tag.name">
      <template #header>
        <div class="card-header">
          <div class="tag-header-info">
            <span class="tag-name">{{ tag.name }}</span>
            <el-tag :type="getTagTypeTag(tag.name)" size="small">
              {{ getTagTypeTitle(tag.name) }}
            </el-tag>
          </div>
          <div class="header-actions">
            <el-text type="info" size="small">
              {{ formatSize(tag.size) }}
            </el-text>
            <el-button
              type="primary"
              size="small"
              @click="goToTagDetail(tag.name)"
            >
              <el-icon><InfoFilled /></el-icon>
              Details
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDeleteTag(tag.name)"
              :loading="deletingTag === tag.name"
            >
              <el-icon><Delete /></el-icon>
              Delete
            </el-button>
          </div>
        </div>
      </template>
      <div class="tag-details">
        <div class="detail-item">
          <el-text type="info">Created:</el-text>
          <el-text>{{ tag.created || 'Unknown' }}</el-text>
        </div>
        <div class="detail-item">
          <el-text type="info">Digest:</el-text>
          <el-text class="digest-text">{{ tag.digest }}</el-text>
        </div>
        <div class="pull-command">
          <el-text type="info">{{ isHelmChart(tag.name) ? 'Helm Command:' : 'Pull Command:' }}</el-text>
          <el-input
            :model-value="getPullCommand(tag)"
            readonly
            class="command-input"
          >
            <template #append>
              <el-button @click="copyToClipboard(getPullCommand(tag))">
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!loading && tags.length === 0" description="No tags found" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, DocumentCopy, Delete, InfoFilled } from '@element-plus/icons-vue'
import { registryApi } from '../api/registry'
import { ElMessage, ElMessageBox } from "element-plus";
import { useProtectedPage } from '../composables/useAuthCheck'

const route = useRoute()
const router = useRouter()

// Decode the repository name from URL to handle special characters
const repositoryName = ref(decodeURIComponent(route.params.name))
const tags = ref([])
const tagManifests = ref({})
const loading = ref(false)
const deletingTag = ref(null)

const fetchRepositoryTags = async () => {
  loading.value = true
  try {
    const data = await registryApi.getRepositoryTags(repositoryName.value)
    if (data.tags) {
      // Fetch detailed info for each tag
      const tagDetails = await Promise.allSettled(
        data.tags.map(async (tag) => {
          try {
            // Fetch both manifest info and full manifest for type detection
            const manifestInfo = await registryApi.getManifestInfo(repositoryName.value, tag)
            const manifest = await registryApi.getManifest(repositoryName.value, tag)

            // Store manifest for type detection
            tagManifests.value[tag] = manifest
            return {
              name: tag,
              digest: manifestInfo.digest || 'Unknown',
              size: manifestInfo.contentLength || 'Unknown',
              created: manifestInfo.createdAt || 'Unknown'
            }
          } catch (error) {
            // Fallback to basic info if manifest fetch fails
            return {
              name: tag,
              digest: 'Unknown',
              size: 'Unknown',
              created: 'Unknown'
            }
          }
        })
      )

      tags.value = tagDetails
        .filter(result => result.status === 'fulfilled')
        .map(result => result.value)
    } else {
      tags.value = []
    }
  } catch (error) {
    ElMessage.error('Failed to fetch repository tags')
    console.error('Error fetching repository tags:', error)
  } finally {
    loading.value = false
  }
}

const getPullCommand = (tag) => {
  if (isHelmChart(tag.name)) {
    return `helm pull oci://${window.location.hostname}:${window.location.port || 80}/${repositoryName.value} --version ${tag.name}`
  }
  return `docker pull ${window.location.hostname}:${window.location.port || 80}/${repositoryName.value}:${tag.name}`
}

const isHelmChart = (tagName) => {
  const manifest = tagManifests.value[tagName]
  if (!manifest) return false

  // Check config media type
  if (manifest.config?.mediaType?.includes('helm')) return true

  // Check layers media types
  if (manifest.layers?.length > 0) {
    return manifest.layers.some(layer =>
      layer.mediaType && layer.mediaType.includes('helm')
    )
  }

  // Check top-level media type
  if (manifest.mediaType?.includes('helm')) return true

  // Check artifact type
  if (manifest.artifactType?.includes('helm')) return true

  return false
}

const isDockerImage = (tagName) => {
  const manifest = tagManifests.value[tagName]
  if (!manifest) return false

  // Check config media type
  if (manifest.config?.mediaType?.includes('image') ||
      manifest.config?.mediaType?.includes('container')) return true

  // Check layers media types
  if (manifest.layers?.length > 0) {
    return manifest.layers.some(layer =>
      layer.mediaType && (
        layer.mediaType.includes('image') ||
        layer.mediaType.includes('rootfs') ||
        layer.mediaType.includes('docker')
      )
    )
  }

  // Check top-level media type
  if (manifest.mediaType?.includes('image') ||
      manifest.mediaType?.includes('docker')) return true

  // Check artifact type
  if (manifest.artifactType?.includes('image') ||
      manifest.artifactType?.includes('docker')) return true

  return false
}

const getTagType = (tagName) => {
  if (isHelmChart(tagName)) return 'helm'
  if (isDockerImage(tagName)) return 'docker'
  return 'unknown'
}

const getTagTypeTitle = (tagName) => {
  const type = getTagType(tagName)
  switch (type) {
    case 'helm': return 'Helm Chart'
    case 'docker': return 'Docker Image'
    default: return 'OCI Artifact'
  }
}

const getTagTypeTag = (tagName) => {
  const type = getTagType(tagName)
  switch (type) {
    case 'helm': return 'success'
    case 'docker': return 'info'
    default: return 'warning'
  }
}

const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('Copied to clipboard')
  } catch (error) {
    ElMessage.error('Failed to copy to clipboard')
  }
}

const formatSize = (size) => {
  if (size === 'Unknown') return size
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

const handleDeleteTag = async (tagName) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete tag "${tagName}"?`,
      'Delete Tag',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )

    deletingTag.value = tagName
    await registryApi.deleteTag(repositoryName.value, tagName)

    ElMessage.success(`Tag "${tagName}" deleted successfully`)

    // Remove the deleted tag from the list
    tags.value = tags.value.filter(tag => tag.name !== tagName)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`Failed to delete tag "${tagName}": ${error.message}`)
    }
  } finally {
    deletingTag.value = null
  }
}

const goToTagDetail = (tagName) => {
  router.push({
    name: 'TagDetail',
    params: { name: repositoryName.value, tag: tagName }
  })
}

const goBack = () => {
  router.back()
}

// Initialize page with auth check
const { initPage } = useProtectedPage(router, fetchRepositoryTags, { loading })
onMounted(() => {
  initPage()
})
</script>

<style scoped>
.repository-container {
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

.tag-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tag-name {
  font-weight: bold;
  font-size: 16px;
}

.tag-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tag-details {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.digest-text {
  font-family: monospace;
  font-size: 12px;
}

.pull-command {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.command-input {
  flex: 1;
}
</style>
