<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="login-header">
          <h2>FUNERAL Registry Login</h2>
        </div>
      </template>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="isAnonymous ? {} : loginRules"
        label-width="80px"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="Username" prop="username" v-if="!isAnonymous">
          <el-input
            v-model="loginForm.username"
            placeholder="Enter username"
            prefix-icon="User"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="Password" prop="password" v-if="!isAnonymous">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="Enter password"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="isAnonymous">
            Anonymous Access
          </el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            {{ isAnonymous ? 'Access as Anonymous' : 'Login' }}
          </el-button>
        </el-form-item>

        <div v-if="errorMessage" class="error-message">
          <el-alert
            :title="errorMessage"
            type="error"
            :closable="false"
            show-icon
          />
        </div>

        <div class="login-info">
          <el-text type="info" size="small">
            Default credentials: admin / password
          </el-text>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loginFormRef = ref()
const loading = ref(false)
const errorMessage = ref('')
const isAnonymous = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: 'Please input username', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please input password', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!isAnonymous.value && !loginFormRef.value) return

  if (!isAnonymous.value) {
    const valid = await loginFormRef.value.validate()
    if (!valid) return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    let result
    if (isAnonymous.value) {
      result = await authStore.loginAnonymous()
    } else {
      result = await authStore.login(loginForm.username, loginForm.password)
    }

    if (result.success) {
      ElMessage.success(isAnonymous.value ? 'Anonymous access enabled' : 'Login successful')

      // Redirect to intended page or home
      const redirect = route.query.redirect || '/'
      router.push(redirect)
    } else {
      errorMessage.value = result.error || (isAnonymous.value ? 'Anonymous access failed' : 'Login failed')
    }
  } catch (error) {
    errorMessage.value = error.message || (isAnonymous.value ? 'Anonymous access failed' : 'Login failed')
  } finally {
    loading.value = false
  }
}

// Auto-fill default credentials for demo
loginForm.username = 'admin'
loginForm.password = 'password'
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f7fa;
  padding: 20px;
  box-sizing: border-box;
}

.login-card {
  width: 400px;
  max-width: 90%;
}

.login-header {
  text-align: center;
}

.login-header h2 {
  margin: 0 0 10px 0;
  color: #409EFF;
  font-size: 24px;
}

.login-form {
  margin-top: 10px;
}

.login-button {
  width: 100%;
  height: 40px;
}

.error-message {
  margin: 10px 0;
}

.login-info {
  text-align: center;
  margin-top: 15px;
}

/* Reduce form item spacing */
:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
</style>
