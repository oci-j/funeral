<template>
  <div class="about-button-wrapper">
    <transition name="slide-fade">
      <div
        v-show="showText"
        class="about-text"
      >
        About
      </div>
    </transition>
    <el-button
      type="primary"
      circle
      @click="openAbout"
      @mouseenter="showText = true"
      @mouseleave="showText = false"
      title="About FUNERAL"
      class="about-button"
    >
      <el-icon class="button-icon"><InfoFilled /></el-icon>
    </el-button>

    <AboutDialog ref="aboutDialogRef" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import AboutDialog from './AboutDialog.vue'

const aboutDialogRef = ref(null)
const showText = ref(false)

const openAbout = () => {
  aboutDialogRef.value?.open?.()
}
</script>

<style scoped>
.about-button-wrapper {
  position: fixed;
  bottom: 20px;
  left: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  z-index: 1000;
}

.about-button {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #409EFF 0%, #337ecc 100%);
  border: none;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.about-button:hover {
  transform: translateY(-4px) scale(1.05);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.4);
  background: linear-gradient(135deg, #53a8ff 0%, #409EFF 100%);
}

.about-button:active {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.35);
}

.button-icon {
  font-size: 24px;
  transition: transform 0.3s ease;
}

.about-button:hover .button-icon {
  transform: rotate(90deg);
}

.about-text {
  background: rgba(64, 158, 255, 0.9);
  color: white;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(4px);
  white-space: nowrap;
}

/* Slide transition for text */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}
</style>
