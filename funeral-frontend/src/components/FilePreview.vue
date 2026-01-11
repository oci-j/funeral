<template>
  <el-dialog
    v-model="dialogVisible"
    :title="fileName"
    width="80%"
    top="5vh"
    :close-on-click-modal="false"
    @closed="closePreview"
  >
    <div class="file-preview-container">
      <!-- Loading state -->
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>Loading file content...</span>
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="error-state">
        <el-alert :title="error" type="error" :closable="false" />
      </div>

      <!-- File metadata -->
      <div v-if="fileHashes" class="file-metadata">
        <div class="hash-info">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="MD5">
              <el-text type="info" class="hash-text">{{ fileHashes.md5 }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="SHA256">
              <el-text type="info" class="hash-text">{{ fileHashes.sha256 }}</el-text>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- Content display -->
      <div class="content-viewer">
        <!-- Text content -->
        <div v-if="contentType === 'text'" class="text-viewer">
          <pre class="text-content">{{ fileContentText }}</pre>
        </div>

        <!-- JSON content -->
        <div v-else-if="contentType === 'json'" class="json-viewer-container">
          <vue-json-pretty
            v-if="vueJsonPrettyAvailable"
            :data="jsonData"
            :deep="3"
            :show-length="true"
            :show-line="true"
            :show-double-quotes="true"
          />
          <pre v-else class="json-content">{{ JSON.stringify(jsonData, null, 2) }}</pre>
        </div>

        <!-- Image content -->
        <div v-else-if="contentType === 'image'" class="image-viewer">
          <img :src="imageSrc" :alt="fileName" class="preview-image" />
        </div>

        <!-- Binary content -->
        <div v-else-if="contentType === 'binary'" class="binary-viewer">
          <el-alert
            title="Binary file content"
            type="warning"
            :closable="false"
            description="This file appears to be binary. Below shows a text representation (may contain unreadable characters)."
          />
          <pre v-if="fileContentText" class="text-content binary-text">{{ fileContentText }}</pre>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="closePreview">Close</el-button>
      <el-button
        v-if="contentType === 'text' || contentType === 'json'"
        type="primary"
        @click="copyToClipboard"
      >
        Copy Content
      </el-button>
      <el-button
        type="primary"
        @click="downloadFile"
      >
        Download File
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  fileName: {
    type: String,
    default: ''
  },
  fileContent: {
    type: [String, ArrayBuffer],
    default: ''
  },
  fileSize: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:visible'])

const loading = ref(false)
const error = ref('')
const contentType = ref('text') // Default to text for binary content
const vueJsonPrettyAvailable = ref(false)
const previewContent = ref('')  // For text content
const fileHashes = ref(null)  // For MD5 and SHA256

// Get file content as string for display
const fileContentText = computed(() => {
  // For text content, return previewContent
  if (contentType.value === 'text' && previewContent.value) {
    return previewContent.value
  }

  // For JSON content from ArrayBuffer, decode it
  if (contentType.value === 'json' && typeof props.fileContent !== 'string') {
    const decoder = new TextDecoder('utf-8')
    try {
      return decoder.decode(props.fileContent)
    } catch (e) {
      return ''
    }
  }

  // For binary content, always try to give a text representation
  if (contentType.value === 'binary' || !previewContent.value) {
    if (props.fileContent instanceof ArrayBuffer) {
      const decoder = new TextDecoder('utf-8', { fatal: false })
      try {
        return decoder.decode(props.fileContent)
      } catch (e) {
        // If UTF-8 fails, try as ISO-8859-1 to show something
        const isoDecoder = new TextDecoder('iso-8859-1')
        return isoDecoder.decode(props.fileContent)
      }
    } else if (typeof props.fileContent === 'string') {
      return props.fileContent
    }
  }

  return previewContent.value || ''
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const fileExtension = computed(() => {
  if (!props.fileName) return ''
  const parts = props.fileName.split('.')
  return parts.length > 1 ? parts.pop().toLowerCase() : ''
})

const jsonData = computed(() => {
  if (contentType.value !== 'json') {
    return null
  }
  try {
    return JSON.parse(fileContentText.value)
  } catch (e) {
    return null
  }
})

const imageSrc = computed(() => {
  if (contentType.value !== 'image' || !(props.fileContent instanceof ArrayBuffer)) {
    return ''
  }
  const blob = new Blob([props.fileContent])
  return URL.createObjectURL(blob)
})

const detectContentType = async () => {
  // Check file extension first
  const textExtensions = ['txt', 'md', 'yaml', 'yml', 'xml', 'sh', 'py', 'js', 'css', 'html', 'conf', 'ini', 'toml', 'log', 'csv']
  const jsonExtensions = ['json']
  const imageExtensions = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'svg', 'ico']

  if (jsonExtensions.includes(fileExtension.value)) {
    contentType.value = 'json'
  } else if (imageExtensions.includes(fileExtension.value)) {
    contentType.value = 'image'
  } else if (textExtensions.includes(fileExtension.value) || props.fileSize < 1024 * 1024) {
    // For small files or files with text-like extensions, try to detect if it's text
    if (typeof props.fileContent === 'string') {
      contentType.value = 'text'
      previewContent.value = props.fileContent
    } else if (props.fileContent instanceof ArrayBuffer) {
      // For ArrayBuffer, check if it looks like text
      const dataView = new DataView(props.fileContent)
      let isText = true
      const maxCheck = Math.min(props.fileContent.byteLength, 1000)

      for (let i = 0; i < maxCheck; i++) {
        const byte = dataView.getUint8(i)
        if (byte > 127) {
          isText = false
          break
        }
      }

      if (isText) {
        contentType.value = 'text'
        // Convert ArrayBuffer to string for text display
        const decoder = new TextDecoder('utf-8')
        try {
          previewContent.value = decoder.decode(props.fileContent)
        } catch (e) {
          contentType.value = 'binary'
        }
      } else {
        contentType.value = 'binary'
      }
    } else {
      contentType.value = 'text'
    }
  } else {
    contentType.value = 'binary'
  }

  // Ensure contentType is always set to something
  if (!contentType.value) {
    contentType.value = 'binary'
  }

  // Try to load vue-json-pretty for JSON display
  if (contentType.value === 'json') {
    try {
      const module = await import('vue-json-pretty')
      await import('vue-json-pretty/lib/styles.css')
      vueJsonPrettyAvailable.value = true
    } catch (e) {
      console.warn('vue-json-pretty not available')
    }
  }
}

// Calculate MD5 using crypto-js
const calculateMD5 = (buffer) => {
  return new Promise((resolve) => {
    try {
      import('crypto-js').then(CryptoJS => {
        let wordArray
        if (buffer instanceof ArrayBuffer) {
          const bytes = new Uint8Array(buffer)
          const len = bytes.length
          const words = []
          for (let i = 0; i < len; i++) {
            words[i >>> 2] |= (bytes[i] & 0xff) << (24 - (i % 4) * 8)
          }
          wordArray = new CryptoJS.lib.WordArray.init(words, len)
        } else {
          // For string content
          resolve(CryptoJS.MD5(buffer).toString())
          return
        }

        resolve(CryptoJS.MD5(wordArray).toString())
      }).catch((err) => {
        console.warn('Failed to import crypto-js:', err)
        resolve('N/A')
      })
    } catch (err) {
      console.warn('MD5 calculation failed:', err)
      resolve('N/A')
    }
  })
}

// Calculate SHA256 using crypto-js
const calculateSHA256 = (buffer) => {
  return new Promise((resolve) => {
    try {
      import('crypto-js').then(CryptoJS => {
        let wordArray
        if (buffer instanceof ArrayBuffer) {
          const bytes = new Uint8Array(buffer)
          const len = bytes.length
          const words = []
          for (let i = 0; i < len; i++) {
            words[i >>> 2] |= (bytes[i] & 0xff) << (24 - (i % 4) * 8)
          }
          wordArray = new CryptoJS.lib.WordArray.init(words, len)
        } else {
          // For string content
          resolve(CryptoJS.SHA256(buffer).toString())
          return
        }

        resolve(CryptoJS.SHA256(wordArray).toString())
      }).catch((err) => {
        console.warn('Failed to import crypto-js:', err)
        resolve('N/A')
      })
    } catch (err) {
      console.warn('SHA256 calculation failed:', err)
      resolve('N/A')
    }
  })
}

const calculateFileHashes = async () => {
  if (!props.fileContent) return null

  try {
    let buffer
    if (props.fileContent instanceof ArrayBuffer) {
      buffer = props.fileContent
    } else if (typeof props.fileContent === 'string') {
      const encoder = new TextEncoder()
      buffer = encoder.encode(props.fileContent).buffer
    } else {
      return null
    }

    // Calculate both hashes using crypto-js
    const [md5, sha256] = await Promise.all([
      calculateMD5(buffer),
      calculateSHA256(buffer)
    ])

    return { md5, sha256 }
  } catch (err) {
    console.warn('Failed to calculate file hashes:', err)
    return { md5: 'N/A', sha256: 'N/A' }
  }
}

const loadPreview = async () => {
  if (!props.fileName || !props.fileContent) {
    error.value = 'No file content to preview'
    return
  }

  loading.value = true
  error.value = ''
  previewContent.value = ''
  fileHashes.value = null

  try {
    // Calculate file hashes
    fileHashes.value = await calculateFileHashes()

    // Detect content type
    await detectContentType()

    // Always set contentType for binary files to ensure they show text representation
    if (!contentType.value) {
      contentType.value = 'binary'
    }

    loading.value = false
  } catch (err) {
    error.value = `Failed to load file preview: ${err.message}`
    loading.value = false
  }
}

const closePreview = () => {
  dialogVisible.value = false
  error.value = ''
  contentType.value = ''
  vueJsonPrettyAvailable.value = false
  fileHashes.value = null
  previewContent.value = ''

  // Clean up object URL for images
  if (imageSrc.value.startsWith('blob:')) {
    URL.revokeObjectURL(imageSrc.value)
  }
}

const copyToClipboard = async () => {
  try {
    await navigator.clipboard.writeText(fileContentText.value)
    ElMessage.success('Copied to clipboard')
  } catch (error) {
    ElMessage.error('Failed to copy to clipboard')
  }
}

const downloadFile = () => {
  const blob = props.fileContent instanceof ArrayBuffer
    ? new Blob([props.fileContent])
    : new Blob([props.fileContent])

  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = props.fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// Watch for dialog opening
watch(() => props.visible, (newVal) => {
  if (newVal) {
    nextTick(() => {
      loadPreview()
    })
  }
})
</script>

<style scoped>
.file-preview-container {
  min-height: 400px;
  max-height: 70vh;
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

.file-metadata {
  padding: 0 10px 20px;
  border-bottom: 1px solid #e4e7ed;
  margin-bottom: 20px;
}

.hash-info {
  max-width: 100%;
}

.hash-text {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  word-break: break-all;
}

.content-viewer {
  padding: 10px;
}

.text-viewer,
.json-viewer-container {
  max-height: 60vh;
  overflow: auto;
}

.text-content,
.json-content {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  padding: 15px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.binary-text {
  margin-top: 20px;
  border: 1px solid #e6a23c;
  background-color: #fdf6ec;
}

.image-viewer {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  padding: 20px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.preview-image {
  max-width: 100%;
  max-height: 60vh;
  object-fit: contain;
}

.binary-viewer {
  padding: 10px;
}

:deep(.vjs-tree) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
}

:deep(.vjs-value) {
  color: #cf222e;
}

:deep(.vjs-value-string) {
  color: #0a3069;
}

:deep(.vjs-key) {
  color: #0969da;
}

:deep(.el-descriptions-item__label) {
  width: 80px;
  text-align: right;
}
</style>
