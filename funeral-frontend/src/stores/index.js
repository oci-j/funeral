import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useRegistryStore = defineStore('registry', () => {
  const repositories = ref([])
  const loading = ref(false)

  const fetchRepositories = async () => {
    loading.value = true
    try {
      const response = await fetch('/v2/_catalog')
      if (response.ok) {
        const data = await response.json()
        repositories.value = data.repositories || []
      }
    } catch (error) {
      console.error('Failed to fetch repositories:', error)
    } finally {
      loading.value = false
    }
  }

  const fetchRepository = async (name) => {
    loading.value = true
    try {
      const response = await fetch(`/v2/${name}/tags/list`)
      if (response.ok) {
        const data = await response.json()
        return data
      }
    } catch (error) {
      console.error('Failed to fetch repository:', error)
    } finally {
      loading.value = false
    }
  }

  const deleteRepository = async (name) => {
    try {
      const response = await fetch(`/v2/${name}/`, {
        method: 'DELETE'
      })
      if (response.ok) {
        await fetchRepositories()
        return true
      }
    } catch (error) {
      console.error('Failed to delete repository:', error)
    }
    return false
  }

  return {
    repositories,
    loading,
    fetchRepositories,
    fetchRepository,
    deleteRepository
  }
})
