<template>
  <div id="app">
    <!-- Login page - full screen without layout -->
    <template v-if="$route.path === '/login'">
      <router-view />
    </template>

    <!-- Main app layout with header/sidebar -->
    <template v-else>
      <el-container style="height: 100vh">
        <el-header>
          <div class="header-content">
            <div class="logo-section">
              <el-button class="menu-toggle show-mobile-inline" :icon="Menu" @click="toggleMobileMenu" circle style="border: none; background: rgba(255,255,255,0.2); color: white; margin-right: 12px"/>
              <img src="/image/funeral.jpg" alt="FUNERAL Logo" class="logo-image" />
              <h2 class="hide-xs">FUNERAL - OCI Registry</h2>
              <h2 class="show-mobile" style="display: none;">FUNERAL</h2>
              <el-button
                type="info"
                :icon="InfoFilled"
                circle
                @click="$refs.aboutDialog.open()"
                title="About FUNERAL"
                style="margin-left: 0;"
                class="hide-xs"
              />
            </div>
            <div class="header-actions">
              <!-- Auth status tag -->
              <div v-if="authStore.isAuthenticated" class="user-menu">
                <el-dropdown @command="handleUserCommand" :disabled = "!authStore.authEnabled">
                  <span class="user-dropdown">
                    <el-icon>
                      <el-icon v-if="authStore.checkingConfig"><Loading /></el-icon>
                      <el-icon v-else><User v-if="authStore.authEnabled" /><Unlock v-else /></el-icon>
                    </el-icon>
                    <span class="hide-xs">{{ authStore.authEnabled ? authStore.user?.username : 'auth disabled' }}</span>
                    <el-icon class="el-icon--right" v-if= "authStore.authEnabled"><arrow-down /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="logout">
                        <el-icon><SwitchButton /></el-icon>
                        Logout
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <div v-else>
                <el-button
                  type="primary"
                  :disabled="!authStore.authEnabled"
                  @click="$router.push('/login')"
                >
                  <span v-if="authStore.authEnabled">Login</span>
                  <span v-else>Login Disabled</span>
                </el-button>
              </div>
            </div>
          </div>
        </el-header>

        <el-container>
          <el-aside width="200px" class="sidebar" :class="{ 'mobile-menu-open': mobileMenuOpen }">
            <div class="sidebar-container">
              <el-menu
                router
                :default-active="$route.path"
                class="el-menu-vertical"
                @select="handleMenuSelect"
              >
                <el-menu-item index="/">
                  <el-icon><HomeFilled /></el-icon>
                  <span>Repositories</span>
                </el-menu-item>
                <el-menu-item index="/upload">
                  <el-icon><UploadFilled /></el-icon>
                  <span>Upload Image</span>
                </el-menu-item>
                <el-menu-item index="/mirror">
                  <el-icon><Download /></el-icon>
                  <span>Mirror Image</span>
                </el-menu-item>
                <el-menu-item index="/mirror-helm">
                  <el-icon><Download /></el-icon>
                  <span>Mirror Helm</span>
                </el-menu-item>
                <el-menu-item v-if="authStore.isAdmin" index="/admin">
                  <el-icon><Tools /></el-icon>
                  <span>Admin</span>
                </el-menu-item>
              </el-menu>
            </div>
          </el-aside>

          <!-- Overlay for mobile menu -->
          <div v-if="mobileMenuOpen" class="el-overlay active" @click="toggleMobileMenu"></div>

          <el-main class="main-content">
            <router-view />
          </el-main>
        </el-container>
      </el-container>

      <AboutDialog ref="aboutDialog" />
    </template>
  </div>
</template>

<script setup>
import {
  HomeFilled,
  UploadFilled,
  Download,
  User,
  SwitchButton,
  Tools,
  Lock,
  Unlock,
  Loading,
  ArrowDown,
  InfoFilled,
  Menu
} from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'
import { useRouter } from 'vue-router'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AboutDialog from './components/AboutDialog.vue'

const authStore = useAuthStore()
const router = useRouter()
const aboutDialog = ref()
const mobileMenuOpen = ref(false)

const authStatusText = computed(() => {
  if (authStore.checkingConfig) return 'Checking Auth...'
  return authStore.authEnabled ? 'Auth Enabled' : 'Auth Disabled (Admin)'
})

const authStatusType = computed(() => {
  if (authStore.checkingConfig) return 'info'
  return authStore.authEnabled ? 'danger' : 'success'
})

const handleUserCommand = (command) => {
  if (command === 'logout') {
    authStore.logout()
    ElMessage.success('Logged out successfully')
    router.push('/login')
  }
}

const handleMenuSelect = (index) => {
  if (index === 'about') {
    aboutDialog.value?.open()
  }
  // Close mobile menu when a menu item is selected
  mobileMenuOpen.value = false
}

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.el-header {
  background-color: #409EFF;
  color: white;
  line-height: 60px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-section h2 {
  margin: 0;
  font-size: 20px;
}

.logo-section .el-button {
  background-color: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  margin-left: 12px;
  transition: all 0.3s ease;
}

.logo-section .el-button:hover {
  background-color: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  transform: translateY(-2px);
}

.logo-image {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.header-content h2 {
  margin: 0;
  font-size: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-menu {
  display: flex;
  align-items: center;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: white;
}

.user-dropdown:hover {
  opacity: 0.8;
}

.auth-status-tag {
  margin-right: 15px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.sidebar {
  background-color: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}

.sidebar-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.el-menu-vertical {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

.sidebar-footer {
  border-top: 1px solid #e4e7ed;
  padding-top: 4px;
}

.sidebar-footer .el-menu-vertical {
  flex: none;
}

/* Mobile responsive styles */
@media (max-width: 768px) {
  .el-header {
    padding: 0 15px;
    line-height: 50px;
    height: 50px !important;
  }

  .logo-section h2 {
    font-size: 18px;
  }

  .logo-image {
    width: 28px;
    height: 28px;
  }

  .el-aside {
    position: fixed !important;
    top: 0;
    left: -250px;
    height: 100vh;
    width: 250px !important;
    z-index: 1000;
    transition: left 0.3s ease;
    background-color: #f5f7fa;
    border-right: 1px solid #e4e7ed;
  }

  .el-aside.mobile-menu-open {
    left: 0;
  }

  .el-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background-color: rgba(0, 0, 0, 0.5);
    z-index: 999;
    display: none;
  }

  .el-overlay.active {
    display: block;
  }

  .menu-toggle {
    display: inline-flex !important;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
  }

  .el-main {
    padding: 15px !important;
  }
}

@media (max-width: 480px) {
  .logo-section h2 {
    font-size: 16px;
  }
}

</style>
