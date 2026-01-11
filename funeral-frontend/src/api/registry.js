import { useAuthStore } from '../stores/auth'

const API_BASE = '/api'

const getAuthHeaders = () => {
  const authStore = useAuthStore()
  const headers = {
    'Content-Type': 'application/json'
  }

  const authHeader = authStore.getAuthHeader()
  if (authHeader) {
    headers['Authorization'] = authHeader
  }

  return headers
}

export const registryApi = {
  async getRepositories() {
    try {
      const response = await fetch(`${API_BASE}/v2/_catalog`, {
        headers: getAuthHeaders()
      })

      // Handle authentication error
      if (response.status === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        throw new Error('Authentication required')
      }

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('Error fetching repositories:', error)
      throw error
    }
  },

  async getRepositoryTags(repositoryName) {
    try {
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/tags/list`, {
        headers: getAuthHeaders()
      })

      if (response.status === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        throw new Error('Authentication required')
      }

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('Error fetching repository tags:', error)
      throw error
    }
  },

  async deleteRepository(repositoryName) {
    try {
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/`, {
        method: 'DELETE',
        headers: getAuthHeaders()
      })

      if (response.status === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        throw new Error('Authentication required')
      }

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return response
    } catch (error) {
      console.error('Error deleting repository:', error)
      throw error
    }
  },

  async checkBlob(repositoryName, digest) {
    try {
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/blobs/${digest}`, {
        method: 'HEAD',
        headers: getAuthHeaders()
      })
      return response.ok
    } catch (error) {
      console.error('Error checking blob:', error)
      return false
    }
  },

  async login(username, password) {
    try {
      const response = await fetch(`${API_BASE}/v2/token`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          username,
          password,
          service: 'funeral-registry',
          scope: 'pull,push'
        })
      })

      if (!response.ok) {
        throw new Error('Invalid credentials')
      }

      return await response.json()
    } catch (error) {
      console.error('Login error:', error)
      throw error
    }
  }
}
