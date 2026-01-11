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

      <!-- Content display -->
      <div v-else class="content-viewer">
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
        <div v-else class="binary-viewer">
          <el-alert
            title="Binary file preview is not available"
            type="info"
            :closable="false"
            description="This file appears to be binary and cannot be previewed in the browser."
          />
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
        v-if="contentType === 'image'"
        type="primary"
        @click="downloadFile"
      >
        Download
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
const contentType = ref('')
const vueJsonPrettyAvailable = ref(false)
const previewContent = ref('')  // For text content

// Get file content as string for display
const fileContentText = computed(() => {
  if (contentType.value === 'json' && typeof props.fileContent !== 'string') {
    // JSON needs to be string for vue-json-pretty
    const decoder = new TextDecoder('utf-8')
    try {
      return decoder.decode(props.fileContent)
    } catch (e) {
      return ''
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

const loadPreview = async () => {
  if (!props.fileName || !props.fileContent) {
    error.value = 'No file content to preview'
    return
  }

  loading.value = true
  error.value = ''

  try {
    await detectContentType()
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

.content-viewer {
  height: 100%;
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
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
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
</style>
