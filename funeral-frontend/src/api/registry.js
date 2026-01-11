const API_BASE = '/api'

export const registryApi = {
  async getRepositories() {
    try {
      const response = await fetch(`${API_BASE}/v2/_catalog`)
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
      const response = await fetch(`${API_BASE}/v2/${repositoryName}/tags/list`)
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
        method: 'DELETE'
      })
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
        method: 'HEAD'
      })
      return response.ok
    } catch (error) {
      console.error('Error checking blob:', error)
      return false
    }
  }
}
