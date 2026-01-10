<template>
  <div class="page-container">
    <!-- Page Header -->
    <div class="page-header" v-if="showHeader">
      <slot name="header">
        <h1>{{ title }}</h1>
        <div class="header-actions" v-if="showActions">
          <slot name="actions"></slot>
        </div>
      </slot>
    </div>

    <!-- Main Content -->
    <div class="page-content">
      <el-card class="content-card" :body-style="cardBodyStyle">
        <template v-if="cardTitle" #header>
          <div class="card-header">
            <span class="card-title">{{ cardTitle }}</span>
            <slot name="card-actions"></slot>
          </div>
        </template>

        <!-- Loading State -->
        <div v-if="loading" class="loading-container">
          <el-loading :text="loadingText"></el-loading>
        </div>

        <!-- Empty State -->
        <el-empty
          v-else-if="!loading && (empty || items.length === 0)"
          :description="emptyText"
          :image-size="emptyImageSize"
        >
          <slot name="empty"></slot>
        </el-empty>

        <!-- Content -->
        <template v-else>
          <slot></slot>
        </template>
      </el-card>
    </div>

    <!-- Footer Actions -->
    <div class="page-footer" v-if="showFooter">
      <slot name="footer"></slot>
    </div>
  </div>

  <!-- About Button - Fixed at bottom left -->
  <div class="about-button-container" v-if="showAboutButton">
    <el-button
      type="info"
      plain
      circle
      @click="openAbout"
      title="About FUNERAL"
    >
      <el-icon><InfoFilled /></el-icon>
    </el-button>
  </div>

  <AboutDialog ref="aboutDialogRef" />
</template>

<script setup>
import { ref } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import AboutDialog from './AboutDialog.vue'

const props = defineProps({
  // Page header
  title: {
    type: String,
    default: ''
  },
  showHeader: {
    type: Boolean,
    default: true
  },
  showActions: {
    type: Boolean,
    default: true
  },

  // Card settings
  cardTitle: {
    type: String,
    default: ''
  },
  cardBodyStyle: {
    type: Object,
    default: () => ({
      padding: '20px'
    })
  },

  // Loading state
  loading: {
    type: Boolean,
    default: false
  },
  loadingText: {
    type: String,
    default: 'Loading...'
  },

  // Empty state
  empty: {
    type: Boolean,
    default: false
  },
  emptyText: {
    type: String,
    default: 'No data found'
  },
  emptyImageSize: {
    type: Number,
    default: 200
  },
  items: {
    type: Array,
    default: () => []
  },

  // Footer
  showFooter: {
    type: Boolean,
    default: false
  },

  // About button
  showAboutButton: {
    type: Boolean,
    default: false
  }
})

const aboutDialogRef = ref(null)

const openAbout = () => {
  aboutDialogRef.value?.open?.()
}
</script>

<style scoped>
.page-container {
  min-height: 100%;
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-content {
  width: 100%;
}

.content-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.page-footer {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* About button styles */
:global(.about-button-container) {
  position: fixed;
  bottom: 20px;
  left: 20px;
  z-index: 1000;
}

:global(.about-button-container .el-button) {
  width: 48px;
  height: 48px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

:global(.about-button-container .el-button:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.15);
}
</style>
