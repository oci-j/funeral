<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="sm:flex sm:items-center sm:justify-between">
      <div>
        <h1 class="text-2xl font-semibold text-gray-900">Container Repositories</h1>
        <p class="mt-2 text-sm text-gray-600">Manage your container images</p>
      </div>
      <router-link
        to="/upload"
        class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
      >
        Upload Image
      </router-link>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="mt-8 flex justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="mt-8 bg-red-50 border border-red-200 rounded-md p-4">
      <div class="flex">
        <XCircleIcon class="h-5 w-5 text-red-400" />
        <div class="ml-3">
          <h3 class="text-sm font-medium text-red-800">Error loading repositories</h3>
          <p class="mt-1 text-sm text-red-700">{{ error }}</p>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else-if="repositories.length === 0" class="mt-12 text-center">
      <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
      </svg>
      <h3 class="mt-2 text-sm font-medium text-gray-900">No repositories</h3>
      <p class="mt-1 text-sm text-gray-500">Start by pushing your first container image</p>
    </div>

    <!-- Repositories List -->
    <div v-else class="mt-8 bg-white shadow overflow-hidden sm:rounded-md">
      <ul class="divide-y divide-gray-200">
        <li v-for="repo in repositories" :key="repo.name" class="px-4 py-4 sm:px-6 hover:bg-gray-50">
          <div class="flex items-center justify-between">
            <div class="flex items-center">
              <div class="flex-shrink-0 h-10 w-10">
                <div class="h-10 w-10 rounded-full bg-blue-100 flex items-center justify-center">
                  <span class="text-blue-600 font-medium text-sm">{{ repo.name.charAt(0).toUpperCase() }}</span>
                </div>
              </div>
              <div class="ml-4">
                <p class="text-sm font-medium text-gray-900">{{ repo.name }}</p>
                <p class="text-sm text-gray-500">{{ repo.tagCount || 0 }} tags</p>
              </div>
            </div>
            <div class="flex items-center space-x-4">
              <time class="text-sm text-gray-500">{{ formatDate(repo.createdAt) }}</time>
              <div class="flex space-x-2">
                <router-link
                  :to="`/repository/${repo.name}`"
                  class="text-blue-600 hover:text-blue-900 text-sm font-medium"
                >
                  View
                </router-link>
                <button
                  @click="deleteRepository(repo.name)"
                  class="text-red-600 hover:text-red-900 text-sm font-medium"
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { XCircleIcon } from '@heroicons/vue/24/outline'
import { useRegistryStore } from '../stores/registry'

const store = useRegistryStore()

const repositories = computed(() => store.repositories)
const loading = computed(() => store.loading)
const error = computed(() => store.error)

const formatDate = computed(() => (dateString) => {
  if (!dateString) return 'Unknown'
  return new Date(dateString).toLocaleDateString()
})

const deleteRepository = async (name) => {
  if (confirm(`Delete repository "${name}"?`)) {
    try {
      console.log('Delete repository:', name)
      // TODO: Implement actual deletion
    } catch (err) {
      alert('Failed to delete repository: ' + err.message)
    }
  }
}

onMounted(() => {
  store.fetchRepositories()
})
</script>
