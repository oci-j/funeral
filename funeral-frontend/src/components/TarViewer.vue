<template>
  <div class="tar-viewer-container">
    <div class="tar-header">
      <el-icon><Box /></el-icon>
      <span>Tar Archive Content</span>
      <el-tag type="success" size="small">
        {{ fileCount }} files, {{ formatSize(totalSize) }}
      </el-tag>
    </div>

    <div class="tar-content">
      <!-- Loading state -->
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>Parsing tar archive...</span>
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="error-state">
        <el-alert :title="error" type="error" :closable="false" />
      </div>

      <!-- File listing -->
      <div v-else class="file-listing">
        <div class="listing-header">
          <span class="header-filename">Filename</span>
          <span class="header-size">Size</span>
          <span class="header-type">Type</span>
        </div>

        <div class="file-tree">
          <div
            v-for="file in sortedFiles"
            :key="file.path"
            class="file-entry"
            :class="{ 'is-directory': file.type === 'directory' }"
          >
            <div class="file-info">
              <el-icon class="file-icon">
                <Folder v-if="file.type === 'directory'" />
                <Document v-else-if="file.type === 'symlink'" />
                <Files v-else />
              </el-icon>
              <span class="file-name" :title="file.path">{{ file.path }}</span>
            </div>
            <span class="file-size">{{ formatSize(file.size) }}</span>
            <span class="file-type">{{ getFileType(file) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Box, Loading, Folder, Document, Files } from '@element-plus/icons-vue'
import pako from 'pako'
import untar from 'js-untar'

const props = defineProps({
  arrayBuffer: {
    type: ArrayBuffer,
    required: true
  },
  mediaType: {
    type: String,
    default: ''
  }
})

const loading = ref(true)
const error = ref('')
const files = ref([])

// Parse tar.gz file using js-untar
const parseTarGz = async () => {
  try {
    console.log('Parsing tar.gz file...', props.arrayBuffer.byteLength, 'bytes')

    let decompressed
    // First, try to decompress gzip
    try {
      decompressed = pako.inflate(props.arrayBuffer)
      console.log('Decompressed size:', decompressed.byteLength, 'bytes')
    } catch (gzipErr) {
      console.warn('Gzip decompression failed, trying raw tar format:', gzipErr.message)
      // If gzip decompression fails, assume it's plain tar
      decompressed = new Uint8Array(props.arrayBuffer)
    }

    // Then parse tar using js-untar
    const arrayBuffer = decompressed.buffer.slice(decompressed.byteOffset, decompressed.byteOffset + decompressed.byteLength)

    try {
      // js-untar returns a promise with progress callback
      const result = await untar(arrayBuffer)
        .progress(file => {
          console.log('Extracting file:', file.name)
        })

      if (!result || result.length === 0) {
        throw new Error('No files found in tar archive or archive is empty')
      }

      const parsedFiles = result.map(entry => ({
        path: entry.name,
        size: entry.size || 0,
        type: getEntryType(entry.type),
        mode: entry.mode || 0,
        mtime: entry.mtime ? new Date(entry.mtime * 1000) : null, // Convert seconds to milliseconds
        uid: entry.uid || 0,
        gid: entry.gid || 0,
        linkname: entry.linkname || null
      }))

      console.log('Tar parsing completed, found', parsedFiles.length, 'files')
      files.value = parsedFiles
      loading.value = false
    } catch (tarErr) {
      console.error('Tar parsing error:', tarErr)
      error.value = `Failed to parse tar archive: ${tarErr.message}`
      loading.value = false
    }

  } catch (err) {
    console.error('Failed to parse layer:', err)
    error.value = err.message
    loading.value = false
  }
}

const getEntryType = (type) => {
  if (!type) return 'file'
  // js-untar uses numeric type codes as strings
  switch (type) {
    case '0':
    case '':
      return 'file'
    case '5':
      return 'directory'
    case '2':
      return 'symlink'
    case '1':
      return 'hardlink'
    default:
      return 'unknown'
  }
}

const formatSize = (size) => {
  if (size === 0) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

const getFileType = (file) => {
  if (file.type === 'directory') return 'Directory'
  if (file.type === 'symlink') return 'Symlink'
  if (file.type === 'hardlink') return 'Hard Link'

  // Try to determine file type from extension
  const ext = file.path.split('.').pop().toLowerCase()
  const typeMap = {
    'sh': 'Shell Script',
    'py': 'Python',
    'js': 'JavaScript',
    'json': 'JSON',
    'yaml': 'YAML',
    'yml': 'YAML',
    'xml': 'XML',
    'txt': 'Text',
    'md': 'Markdown',
    'conf': 'Config',
    'ini': 'Config',
    'toml': 'Config',
    'html': 'HTML',
    'css': 'CSS',
    'bin': 'Binary',
    'so': 'Shared Lib',
    'dll': 'Dynamic Lib',
    'exe': 'Executable'
  }
  return typeMap[ext] || 'File'
}

// Computed properties
const sortedFiles = computed(() => {
  return [...files.value].sort((a, b) => {
    // Directories first
    if (a.type === 'directory' && b.type !== 'directory') return -1
    if (a.type !== 'directory' && b.type === 'directory') return 1
    // Then sort by path
    return a.path.localeCompare(b.path)
  })
})

const fileCount = computed(() => files.value.length)

const totalSize = computed(() => {
  return files.value.reduce((sum, file) => sum + file.size, 0)
})

// Parse on mount
onMounted(() => {
  parseTarGz()
})
</script>

<style scoped>
.tar-viewer-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tar-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
  border-bottom: 1px solid #e4e7ed;
  font-size: 16px;
  font-weight: 500;
}

.tar-content {
  flex: 1;
  overflow: auto;
}

.loading-state,
.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 8px;
}

.file-listing {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.listing-header {
  display: grid;
  grid-template-columns: 1fr 100px 120px;
  gap: 16px;
  padding: 12px 16px;
  background-color: #f5f7fa;
  border-bottom: 2px solid #e4e7ed;
  font-weight: 600;
  font-size: 14px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.file-tree {
  flex: 1;
  overflow-y: auto;
}

.file-entry {
  display: grid;
  grid-template-columns: 1fr 100px 120px;
  gap: 16px;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
  align-items: center;
  transition: background-color 0.2s;
}

.file-entry:hover {
  background-color: #f9f9f9;
}

.file-entry.is-directory {
  background-color: #f8fbff;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.file-icon {
  flex-shrink: 0;
  color: #909399;
}

.file-name {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 13px;
  color: #606266;
  text-align: right;
}

.file-type {
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
}

.file-entry.is-directory .file-name {
  font-weight: 600;
  color: #303133;
}

.file-entry.is-directory .file-icon {
  color: #409eff;
}
</style>
