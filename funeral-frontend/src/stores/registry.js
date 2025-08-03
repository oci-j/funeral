import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import registryAPI from '../api/registry'

export const useRegistryStore = defineStore('registry', () => {
  const repositories = ref([])
  const loading = ref(false)
  const error = ref(null)

  const repositoryCount = computed(() => repositories.value.length)

  async function fetchRepositories() {
    loading.value = true
    error.value = null
    try {
      repositories.value = await registryAPI.getRepositories()
    } catch (err) {
      error.value = err.message
    } finally {
      loading.value = false
    }
  }

  async function getRepository(name) {
    try {
      return await registryAPI.getRepository(name)
    } catch (err) {
      error.value = err.message
      throw err
    }
  }

  async function getTags(repositoryName) {
    try {
      return await registryAPI.getTags(repositoryName)
    } catch (err) {
      error.value = err.message
      throw err
    }
  }

  async function getManifest(repositoryName, reference) {
    try {
      return await registryAPI.getManifest(repositoryName, reference)
    } catch (err) {
      error.value = err.message
      throw err
    }
  }

  async function deleteManifest(repositoryName, reference) {
    try {
      await registryAPI.deleteManifest(repositoryName, reference)
      // Refresh repositories after deletion
      await fetchRepositories()
    } catch (err) {
      error.value = err.message
      throw err
    }
  }

  return {
    repositories,
    loading,
    error,
    repositoryCount,
    fetchRepositories,
    getRepository,
    getTags,
    getManifest,
    deleteManifest
  }
})
