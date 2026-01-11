import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.roles?.includes('ADMIN') || false)

  const login = async (username, password) => {
    try {
      const response = await fetch('/api/v2/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          username,
          password,
          service: 'funeral-registry',
          scope: 'pull,push'
        })
      })

      if (!response.ok) {
        throw new Error('Login failed')
      }

      const data = await response.json()
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
    isAuthenticated,
    isAdmin,
    login,
    logout,
    getAuthHeader
  }
})
