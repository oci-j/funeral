<template>
  <div class="tar-viewer-container">
    <div class="tar-header">
      <el-icon><Box /></el-icon>
      <span>Tar Archive Content</span>
      <div class="header-stats">
        <el-tag type="info" size="small">
          {{ folderCount }} folders
        </el-tag>
        <el-tag type="success" size="small">
          {{ fileCount }} files
        </el-tag>
        <el-tag type="warning" size="small">
          {{ formatSize(totalSize) }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button-group size="small">
          <el-button @click="toggleExpandAll">
            <el-icon><component :is="allExpanded ? 'Fold' : 'Expand'" /></el-icon>
            {{ allExpanded ? 'Collapse All' : 'Expand All' }}
          </el-button>
          <el-button @click="toggleEmptyFolders">
            <el-icon><View /></el-icon>
            {{ showEmptyFolders ? 'Hide Empty' : 'Show Empty' }}
          </el-button>
        </el-button-group>
      </div>
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

      <!-- File tree -->
      <div v-else-if="treeData.length > 0" class="file-tree-container">
        <div class="tree-header">
          <span class="header-name">Name</span>
          <span class="header-size">Size</span>
          <span class="header-type">Type</span>
        </div>

        <div class="tree-view">
          <TreeItem
            v-for="node in treeData"
            :key="node.path"
            :node="node"
            :level="0"
          />
        </div>
      </div>

      <!-- Empty state -->
      <div v-else class="empty-state">
        <el-empty description="No files in archive" />
      </div>
    </div>
  </div>

  <!-- File Preview Dialog -->
  <FilePreview
    v-model:visible="previewVisible"
    :file-name="previewFile.name"
    :file-content="previewFile.content"
    :file-size="previewFile.size"
  />
</template>

<script setup>
import { ref, computed, onMounted, provide } from 'vue'
import FilePreview from './FilePreview.vue'
import { Box, Loading, Expand, Fold, View } from '@element-plus/icons-vue'
import pako from 'pako'
import untar from 'js-untar'
import TreeItem from './TreeItem.vue'

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
const treeData = ref([])
const allExpanded = ref(true)
const showEmptyFolders = ref(true)

// File preview state
const previewVisible = ref(false)
const previewFile = ref({
  name: '',
  content: null,
  size: 0
})

// Provide state to child components
provide('allExpanded', allExpanded)
provide('showEmptyFolders', showEmptyFolders)

// Provide utilities to child components
provide('formatSize', formatSize)
provide('getFileType', getFileType)

// Provide preview function
provide('previewFile', (file) => {
  if (file.type === 'file' && file.content) {
    previewFile.value = {
      name: file.path || file.name,
      content: file.content,
      size: file.size
    }
    previewVisible.value = true
  }
})

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
        linkname: entry.linkname || null,
        content: entry.buffer || null // Store file content for preview
      }))

      console.log('Tar parsing completed, found', parsedFiles.length, 'files')
      files.value = parsedFiles

      // Build tree structure
      treeData.value = buildTree(parsedFiles)
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

function formatSize(size) {
  if (size === 0) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

function getFileType(file) {
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

// Build tree structure from flat file list
function buildTree(files) {
  const root = { name: '', path: '', type: 'directory', children: [] }
  const pathMap = new Map()
  pathMap.set('', root)

  // Sort files by path depth to ensure parent directories are created first
  const sortedFiles = [...files].sort((a, b) => {
    const depthA = a.path.split('/').filter(p => p).length
    const depthB = b.path.split('/').filter(p => p).length
    return depthA - depthB
  })

  sortedFiles.forEach(file => {
    const parts = file.path.split('/').filter(p => p)
    let currentPath = ''
    let parent = root

    // Create intermediate directories (for all files and directories)
    for (let i = 0; i < parts.length - (file.type === 'file' ? 1 : 0); i++) {
      const part = parts[i]
      const path = currentPath ? `${currentPath}/${part}` : part

      if (!pathMap.has(path)) {
        const dirNode = {
          name: part,
          path: path,
          type: 'directory',
          size: 0,
          children: []
        }
        pathMap.set(path, dirNode)
        parent.children.push(dirNode)
      }

      currentPath = path
      parent = pathMap.get(path)
    }

    // For file entries, add them to their parent directory
    if (file.type === 'file' || file.type === 'symlink' || file.type === 'hardlink') {
      const fileName = parts[parts.length - 1]
      const filePath = file.path

      // Skip if already exists (handles duplicate entries)
      if (!pathMap.has(filePath)) {
        const fileNode = {
          ...file,
          name: fileName,
          path: filePath,
          children: undefined
        }
        pathMap.set(filePath, fileNode)
        parent.children.push(fileNode)
      }
    }
    // For directory entries, they've already been created in the intermediate loop above
    // so we don't need to add them again
  })

  // Sort children in each node (directories first, then files alphabetically)
  const sortNodeChildren = (node) => {
    if (node.children && node.children.length > 0) {
      node.children.sort((a, b) => {
        if (a.type === 'directory' && b.type !== 'directory') return -1
        if (a.type !== 'directory' && b.type === 'directory') return 1
        return a.name.localeCompare(b.name)
      })
      node.children.forEach(sortNodeChildren)
    }
  }
  sortNodeChildren(root)

  return root.children
}

// Toggle functions
const toggleExpandAll = () => {
  allExpanded.value = !allExpanded.value
}

const toggleEmptyFolders = () => {
  showEmptyFolders.value = !showEmptyFolders.value
}

// Computed properties
const fileCount = computed(() => {
  return files.value.filter(f => f.type === 'file' || f.type === 'symlink' || f.type === 'hardlink').length
})

const folderCount = computed(() => {
  const folderPaths = new Set()
  files.value.forEach(file => {
    const parts = file.path.split('/').filter(p => p)
    let currentPath = ''
    for (let i = 0; i < parts.length - (file.type === 'file' ? 1 : 0); i++) {
      const part = parts[i]
      currentPath = currentPath ? `${currentPath}/${part}` : part
      folderPaths.add(currentPath)
    }
  })
  return folderPaths.size
})

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
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
  border-bottom: 1px solid #e4e7ed;
  font-size: 16px;
  font-weight: 500;
}

.header-stats {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.header-actions {
  display: flex;
  gap: 8px;
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

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.file-tree-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tree-header {
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

.tree-view {
  flex: 1;
  overflow-y: auto;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}
</style>
