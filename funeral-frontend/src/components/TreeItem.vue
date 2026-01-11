<template>
  <div class="tree-item" v-if="isVisible">
    <div
      class="tree-row"
      :class="{ 'is-directory': node.type === 'directory', 'is-file': node.type === 'file' }"
      :style="{ paddingLeft: (level * 20 + 16) + 'px' }"
      @click="toggle"
    >
      <div class="row-content">
        <div class="item-info">
          <!-- Expand/Collapse icon for directories -->
          <el-icon v-if="node.type === 'directory'" class="expand-icon">
            <component :is="expanded ? 'Minus' : 'Plus'" />
          </el-icon>
          <div v-else class="indent-spacer"></div>

          <!-- File/Folder icon -->
          <el-icon class="file-icon">
            <Folder v-if="node.type === 'directory'" />
            <Document v-else-if="node.type === 'symlink'" />
            <Files v-else />
          </el-icon>

          <!-- Name -->
          <span class="item-name" :title="node.path">{{ node.name }}</span>
        </div>

        <!-- Size (only for files) -->
        <span v-if="node.type !== 'directory'" class="item-size">
          {{ formatSize(node.size) }}
        </span>
        <div v-else class="size-placeholder"></div>

        <!-- Type -->
        <span class="item-type">{{ getFileType(node) }}</span>
      </div>
    </div>

    <!-- Children -->
    <div v-if="node.children && node.children.length > 0" class="tree-children">
      <TreeItem
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :level="level + 1"
        v-show="expanded"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, inject, computed, watch } from 'vue'
import { Folder, Document, Files, Plus, Minus } from '@element-plus/icons-vue'

const props = defineProps({
  node: {
    type: Object,
    required: true
  },
  level: {
    type: Number,
    default: 0
  }
})

const formatSize = inject('formatSize')
const getFileType = inject('getFileType')
const allExpanded = inject('allExpanded')
const showEmptyFolders = inject('showEmptyFolders')

const expanded = ref(props.node.type === 'directory')

// Watch for allExpanded changes
watch(allExpanded, (newVal) => {
  if (props.node.type === 'directory') {
    expanded.value = newVal
  }
})

// Check if folder has non-directory children (files)
const hasFiles = computed(() => {
  if (props.node.type !== 'directory' || !props.node.children) return false
  return props.node.children.some(child => child.type === 'file' || child.type === 'symlink' || child.type === 'hardlink')
})

// Check if folder should be visible (hide empty folders when appropriate)
const isVisible = computed(() => {
  if (props.node.type !== 'directory') return true
  if (showEmptyFolders.value) return true
  return hasFiles.value
})

const toggle = () => {
  if (props.node.type === 'directory') {
    expanded.value = !expanded.value
  }
}
</script>

<style scoped>
.tree-item {
  border-bottom: 1px solid #f0f0f0;
}

.tree-row {
  cursor: pointer;
  transition: background-color 0.2s;
}

.tree-row:hover {
  background-color: #f9f9f9;
}

.tree-row.is-directory {
  background-color: #f8fbff;
}

.tree-row.is-directory:hover {
  background-color: #e8f4ff;
}

.row-content {
  display: grid;
  grid-template-columns: 1fr 100px 120px;
  gap: 16px;
  padding: 10px 0;
}

.item-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.expand-icon {
  flex-shrink: 0;
  color: #909399;
  font-size: 14px;
  transition: transform 0.2s;
}

.indent-spacer {
  width: 14px;
  flex-shrink: 0;
}

.file-icon {
  flex-shrink: 0;
  color: #909399;
  font-size: 16px;
}

.tree-row.is-directory .file-icon {
  color: #409eff;
}

.item-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-row.is-directory .item-name {
  font-weight: 600;
  color: #303133;
}

.item-size {
  font-size: 13px;
  color: #606266;
  text-align: right;
  padding-right: 16px;
}

.size-placeholder {
  width: 100px;
}

.item-type {
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
  padding-right: 16px;
}

.tree-children {
  overflow: hidden;
}

/* Transition for expand/collapse */
.tree-children {
  transition: max-height 0.3s ease-out;
}

.tree-children[style*="display: none"] {
  max-height: 0;
}
</style>
