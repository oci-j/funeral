<template>
  <div id="app">
    <el-container style="height: 100vh">
      <el-header>
        <div class="header-content">
          <div class="logo-section">
            <img src="/image/funeral.png" alt="FUNERAL Logo" class="logo-image" />
            <h2>FUNERAL - OCI Registry</h2>
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
                  {{ authStore.authEnabled ? authStore.user?.username : 'auth disabled' }}
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
        <el-aside width="200px" class="sidebar">
          <div class="sidebar-container">
            <el-menu
              router
              :default-active="$route.path"
              class="el-menu-vertical"
            >
              <el-menu-item index="/">
                <el-icon><HomeFilled /></el-icon>
                <span>Repositories</span>
              </el-menu-item>
              <el-menu-item index="/upload">
                <el-icon><UploadFilled /></el-icon>
                <span>Upload Image</span>
              </el-menu-item>
              <el-menu-item v-if="authStore.isAdmin" index="/admin">
                <el-icon><Tools /></el-icon>
                <span>Admin</span>
              </el-menu-item>
            </el-menu>

            <div class="sidebar-footer">
              <el-menu
                class="el-menu-vertical"
                @select="handleMenuSelect"
              >
                <el-menu-item index="about">
                  <el-icon><InfoFilled /></el-icon>
                  <span>About</span>
                </el-menu-item>
              </el-menu>
            </div>
          </div>
        </el-aside>

        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>

    <AboutDialog ref="aboutDialog" />
  </div>
</template>

<script setup>
import {
  HomeFilled,
  UploadFilled,
  User,
  SwitchButton,
  Tools,
  Lock,
  Unlock,
  Loading,
  ArrowDown,
  InfoFilled
} from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'
import { useRouter } from 'vue-router'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AboutDialog from './components/AboutDialog.vue'

const authStore = useAuthStore()
const router = useRouter()
const aboutDialog = ref()

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
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
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
  margin-left: auto;
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
</style>
