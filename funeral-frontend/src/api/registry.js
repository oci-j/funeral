import { useAuthStore } from '../stores/auth'

const API_BASE = ''

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

  async deleteTag(repositoryName, tag) {
    try {
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/manifests/${tag}`, {
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
      console.error('Error deleting tag:', error)
      throw error
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
  },

  async loginAnonymous() {
    try {
      // Call token endpoint without authentication headers for anonymous access
      const response = await fetch(`${API_BASE}/v2/token?service=funeral-registry&scope=repository:*:pull`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        }
      })

      if (!response.ok) {
        throw new Error('Anonymous access not supported')
      }

      return await response.json()
    } catch (error) {
      console.error('Anonymous login error:', error)
      throw error
    }
  },

  // Admin API endpoints
  async getUsers() {
    try {
      const response = await fetch(`${API_BASE}/funeral_addition/admin/users`, {
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
      console.error('Error fetching users:', error)
      throw error
    }
  },

  async createUser(userData) {
    try {
      const response = await fetch(`${API_BASE}/funeral_addition/admin/users`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(userData)
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
      console.error('Error creating user:', error)
      throw error
    }
  },

  async updateUser(username, userData) {
    try {
      const response = await fetch(`${API_BASE}/funeral_addition/admin/users/${username}`, {
        method: 'PUT',
        headers: getAuthHeaders(),
        body: JSON.stringify(userData)
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
      console.error('Error updating user:', error)
      throw error
    }
  },

  async deleteUser(username) {
    try {
      const response = await fetch(`${API_BASE}/funeral_addition/admin/users/${username}`, {
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
      console.error('Error deleting user:', error)
      throw error
    }
  },

  async getUserPermissions(username) {
    try {
      const response = await fetch(`${API_BASE}/funeral_addition/admin/permissions/${username}`, {
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
      console.error('Error fetching user permissions:', error)
      throw error
    }
  },

  async setUserPermission(username, repository, permissionData) {
    try {
      const response = await fetch(`${API_BASE}/funeral_addition/admin/permissions/${username}/${repository}`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(permissionData)
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
      console.error('Error setting user permission:', error)
      throw error
    }
  },

  async deleteUserPermission(username, repository) {
    try {
      const response = await fetch(`${API_BASE}/funeral_addition/admin/permissions/${username}/${repository}`, {
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
      console.error('Error deleting user permission:', error)
      throw error
    }
  },

  async getManifest(repositoryName, reference) {
    try {
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/manifests/${reference}`, {
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
      console.error('Error fetching manifest:', error)
      throw error
    }
  },

  async getBlobContent(repositoryName, digest, expectedMediaType = '') {
    try {
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/blobs/${digest}`, {
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

      // Return both text and blob for different content types
      const contentType = response.headers.get('content-type') || ''
      // Check for JSON types including vendor-specific JSON types
      const lowerContentType = contentType.toLowerCase()
      const lowerExpectedMediaType = expectedMediaType.toLowerCase()

      // Check if it's JSON based on content-type or expected media type
      const isJson = lowerContentType.includes('application/json') ||
                     lowerContentType.includes('text/') ||
                     lowerContentType.includes('+json') ||
                     lowerExpectedMediaType.includes('+json')

      if (isJson) {
        const text = await response.text()
        console.log(`Blob content type: ${contentType}, media type: ${expectedMediaType}, size: ${text.length} characters`)
        return { type: 'text', content: text, contentType, mediaType: expectedMediaType }
      } else {
        const blob = await response.blob()
        console.log(`Blob content type: ${contentType}, media type: ${expectedMediaType}, size: ${blob.size} bytes (binary)`)
        return { type: 'blob', content: blob, contentType, mediaType: expectedMediaType }
      }
    } catch (error) {
      console.error('Error fetching blob content:', error)
      throw error
    }
  }
}
