<template>
  <div id="app">
    <el-container style="height: 100vh">
      <el-header>
        <div class="header-content">
          <h2>FUNERAL - OCI Registry</h2>
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
import { HomeFilled, UploadFilled } from '@element-plus/icons-vue'
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

.sidebar {
  background-color: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}

.el-menu-vertical {
  border-right: none;
}
</style>
