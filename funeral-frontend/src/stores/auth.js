import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { registryApi } from '../api/registry'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
  const authEnabled = ref(null) // null = not checked yet, true = enabled, false = disabled
  const checkingConfig = ref(false) // Track if we're currently checking the config

  const isAuthenticated = computed(() => {
    // If auth config not loaded yet, assume enabled for security
    if (authEnabled.value === null) return false
    return authEnabled.value ? !!token.value : true
  })
  // When auth is disabled, treat all users as admin
  const isAdmin = computed(() => authEnabled.value ? (user.value?.roles?.includes('ADMIN') || false) : false)

  const checkAuthConfig = async () => {
    // If already checked or currently checking, return immediately
    if (authEnabled.value !== null || checkingConfig.value) {
      return
    }

    checkingConfig.value = true
    try {
      const response = await fetch('/funeral_addition/config/auth')
      if (!response.ok) {
        throw new Error('Failed to fetch auth config')
      }
      const config = await response.json()
      authEnabled.value = config.enabled
      console.log("Auth config fetched:", config)
    } catch (error) {
      console.error('Failed to check auth config, defaulting to enabled:', error)
      authEnabled.value = true // Default to secure mode
    } finally {
      checkingConfig.value = false
    }
  }

  const login = async (username, password) => {
    try {
      const data = await registryApi.login(username, password)
      token.value = data.access_token || data.token

      // Decode JWT to get user info
      const payload = JSON.parse(atob(token.value.split('.')[1]))
      user.value = {
        username: payload.sub,
        roles: Array.isArray(payload.groups) ? payload.groups : []
      }

      // Save to localStorage
      localStorage.setItem('token', token.value)
      localStorage.setItem('user', JSON.stringify(user.value))

      return { success: true }
    } catch (error) {
      console.error('Login error:', error)
      return { success: false, error: error.message }
    }
  }

  const loginAnonymous = async () => {
    try {
      const data = await registryApi.loginAnonymous()
      token.value = data.access_token || data.token

      // Anonymous user info
      user.value = {
        username: 'anonymous',
        roles: []
      }

      // Save to localStorage
      localStorage.setItem('token', token.value)
      localStorage.setItem('user', JSON.stringify(user.value))

      return { success: true }
    } catch (error) {
      console.error('Anonymous login error:', error)
      return { success: false, error: error.message }
    }
  }

  const logout = () => {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  const getAuthHeader = () => {
    return token.value ? `Bearer ${token.value}` : ''
  }

  // Initialize from localStorage
  if (token.value) {
    try {
      const payload = JSON.parse(atob(token.value.split('.')[1]))
      // Check if token is expired
      if (payload.exp && payload.exp * 1000 < Date.now()) {
        logout()
      }
    } catch (error) {
      console.error('Invalid token:', error)
      logout()
    }
  }

  return {
    token,
    user,
    authEnabled,
    checkingConfig,
    isAuthenticated,
    isAdmin,
    login,
    loginAnonymous,
    logout,
    getAuthHeader,
    checkAuthConfig
  }
})
