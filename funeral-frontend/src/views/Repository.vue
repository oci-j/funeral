<template>
  <div>
    <div class="md:flex md:items-center md:justify-between">
      <div class="flex-1 min-w-0">
        <nav class="flex" aria-label="Breadcrumb">
          <ol role="list" class="flex items-center space-x-4">
            <li>
              <div>
                <router-link to="/" class="text-gray-400 hover:text-gray-500">
                  <span class="sr-only">Home</span>
                  Repositories
                </router-link>
              </div>
            </li>
            <li>
              <div class="flex items-center">
                <svg class="flex-shrink-0 h-5 w-5 text-gray-300" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd" />
                </svg>
                <span class="ml-4 text-sm font-medium text-gray-500">{{ name }}</span>
              </div>
            </li>
          </ol>
        </nav>
        <h2 class="mt-2 text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
          {{ name }}
        </h2>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="mt-8 text-center">
      <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      <p class="mt-2 text-sm text-gray-500">Loading repository details...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="mt-8 rounded-md bg-red-50 p-4">
      <div class="flex">
        <div class="flex-shrink-0">
          <XCircleIcon class="h-5 w-5 text-red-400" />
        </div>
        <div class="ml-3">
          <h3 class="text-sm font-medium text-red-800">Error loading repository</h3>
          <div class="mt-2 text-sm text-red-700">
            <p>{{ error }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Repository Details -->
    <div v-else class="mt-8">
      <!-- Stats -->
      <div class="grid grid-cols-1 gap-5 sm:grid-cols-3">
        <div class="bg-white overflow-hidden shadow rounded-lg">
          <div class="p-5">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <TagIcon class="h-6 w-6 text-gray-400" />
              </div>
              <div class="ml-5 w-0 flex-1">
                <dl>
                  <dt class="text-sm font-medium text-gray-500 truncate">Total Tags</dt>
                  <dd class="text-lg font-medium text-gray-900">{{ tags.length }}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-white overflow-hidden shadow rounded-lg">
          <div class="p-5">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <ClockIcon class="h-6 w-6 text-gray-400" />
              </div>
              <div class="ml-5 w-0 flex-1">
                <dl>
                  <dt class="text-sm font-medium text-gray-500 truncate">Last Updated</dt>
                  <dd class="text-lg font-medium text-gray-900">{{ formatDate(lastUpdated) }}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-white overflow-hidden shadow rounded-lg">
          <div class="p-5">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <CubeIcon class="h-6 w-6 text-gray-400" />
              </div>
              <div class="ml-5 w-0 flex-1">
                <dl>
                  <dt class="text-sm font-medium text-gray-500 truncate">Repository</dt>
                  <dd class="text-lg font-medium text-gray-900">{{ name }}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Tags Table -->
      <div class="mt-8">
        <div class="sm:flex sm:items-center">
          <div class="sm:flex-auto">
            <h3 class="text-lg leading-6 font-medium text-gray-900">Tags</h3>
            <p class="mt-2 text-sm text-gray-700">A list of all tags in this repository.</p>
          </div>
        </div>

        <div v-if="tags.length === 0" class="mt-8 text-center">
          <TagIcon class="mx-auto h-12 w-12 text-gray-400" />
          <h3 class="mt-2 text-sm font-medium text-gray-900">No tags</h3>
          <p class="mt-1 text-sm text-gray-500">This repository doesn't have any tags yet.</p>
        </div>

        <div v-else class="mt-8 flex flex-col">
          <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
            <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
              <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg">
                <table class="min-w-full divide-y divide-gray-300">
                  <thead class="bg-gray-50">
                    <tr>
                      <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Tag
                      </th>
                      <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Digest
                      </th>
                      <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Created
                      </th>
                      <th scope="col" class="relative px-6 py-3">
                        <span class="sr-only">Actions</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody class="bg-white divide-y divide-gray-200">
                    <tr v-for="tag in tags" :key="tag" class="hover:bg-gray-50">
                      <td class="px-6 py-4 whitespace-nowrap">
                        <div class="flex items-center">
                          <TagIcon class="h-5 w-5 text-gray-400 mr-3" />
                          <div class="text-sm font-medium text-gray-900">{{ tag }}</div>
                        </div>
                      </td>
                      <td class="px-6 py-4 whitespace-nowrap">
                        <div class="text-sm text-gray-900 font-mono">
                          {{ getTagDigest(tag) }}
                        </div>
                      </td>
                      <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {{ formatDate(getTagCreatedDate(tag)) }}
                      </td>
                      <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                        <button
                          @click="viewManifest(tag)"
                          class="text-blue-600 hover:text-blue-900 mr-4"
                        >
                          View
                        </button>
                        <button
                          @click="deleteTag(tag)"
                          class="text-red-600 hover:text-red-900"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Manifest Modal -->
      <div v-if="selectedManifest" class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-10" @click="closeManifest">
        <div class="relative top-20 mx-auto p-5 border w-11/12 md:w-3/4 lg:w-1/2 shadow-lg rounded-md bg-white" @click.stop>
          <div class="mt-3">
            <div class="flex items-center justify-between">
              <h3 class="text-lg font-medium text-gray-900">Manifest: {{ selectedTag }}</h3>
              <button @click="closeManifest" class="text-gray-400 hover:text-gray-600">
                <XMarkIcon class="h-6 w-6" />
              </button>
            </div>
            <div class="mt-4">
              <div class="bg-gray-50 rounded-lg p-4">
                <pre class="text-sm text-gray-800 whitespace-pre-wrap">{{ JSON.stringify(selectedManifest, null, 2) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { XCircleIcon, TagIcon, ClockIcon, CubeIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useRegistryStore } from '../stores/registry'

const props = defineProps({
  name: {
    type: String,
    required: true
  }
})

const store = useRegistryStore()
const loading = ref(false)
const error = ref(null)
const tags = ref([])
const lastUpdated = ref(null)
const selectedManifest = ref(null)
const selectedTag = ref(null)
const manifests = ref({})

onMounted(async () => {
  await loadRepository()
})

async function loadRepository() {
  loading.value = true
  error.value = null
  try {
    const response = await store.getTags(props.name)
    tags.value = response.tags || []
    lastUpdated.value = new Date().toISOString()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function viewManifest(tag) {
  try {
    const manifest = await store.getManifest(props.name, tag)
    selectedManifest.value = manifest.content
    selectedTag.value = tag
    manifests.value[tag] = manifest
  } catch (err) {
    alert('Failed to load manifest: ' + err.message)
  }
}

function closeManifest() {
  selectedManifest.value = null
  selectedTag.value = null
}

async function deleteTag(tag) {
  if (confirm(`Are you sure you want to delete tag "${tag}"?`)) {
    try {
      await store.deleteManifest(props.name, tag)
      await loadRepository()
    } catch (err) {
      alert('Failed to delete tag: ' + err.message)
    }
  }
}

function formatDate(dateString) {
  if (!dateString) return 'Unknown'
  return new Date(dateString).toLocaleDateString()
}

function getTagDigest(tag) {
  const manifest = manifests.value[tag]
  return manifest?.digest?.substring(0, 16) + '...' || 'Loading...'
}

function getTagCreatedDate(tag) {
  return new Date().toISOString() // This would come from the manifest in a real implementation
}
</script>
