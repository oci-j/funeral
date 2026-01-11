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
      const response = await fetch(`${API_BASE}/v2/repositories`, {
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

  async getManifestInfo(repositoryName, tag) {
    try {
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/manifests/${tag}/info`, {
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

      const data = await response.json()
      return data
    } catch (error) {
      console.error('Error fetching manifest info:', error)
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
      const credentials = btoa(`${username}:${password}`)
      const response = await fetch(`${API_BASE}/v2/token?service=funeral-registry&scope=repository:*:pull,push`, {
        method: 'POST',
        headers: {
          'Authorization': `Basic ${credentials}`
        }
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
