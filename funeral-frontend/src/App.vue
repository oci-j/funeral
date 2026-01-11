<template>
  <div id="app">
    <el-container style="height: 100vh">
      <el-header>
        <div class="header-content">
          <h2>FUNERAL - OCI Registry</h2>
          <div class="header-actions">
            <div v-if="authStore.isAuthenticated" class="user-menu">
              <el-dropdown @command="handleUserCommand">
                <span class="user-dropdown">
                  <el-icon><User /></el-icon>
                  {{ authStore.user?.username }}
                  <el-icon class="el-icon--right"><arrow-down /></el-icon>
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
              <el-button type="primary" @click="$router.push('/login')">
                Login
              </el-button>
            </div>
          </div>
        </div>
      </el-header>

      <el-container>
        <el-aside width="200px" class="sidebar">
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
          </el-menu>
        </el-aside>

        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { HomeFilled, UploadFilled, User, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const handleUserCommand = (command) => {
  if (command === 'logout') {
    authStore.logout()
    ElMessage.success('Logged out successfully')
    router.push('/login')
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

.sidebar {
  background-color: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}

.el-menu-vertical {
  border-right: none;
}
</style>
