<template>
  <div>
    <div class="md:flex md:items-center md:justify-between">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
          Upload Container Image
        </h2>
        <p class="mt-1 text-sm text-gray-500">
          Upload container images to the registry using Docker commands.
        </p>
      </div>
    </div>

    <div class="mt-8 max-w-3xl">
      <!-- Upload Instructions -->
      <div class="bg-blue-50 border border-blue-200 rounded-lg p-6">
        <h3 class="text-lg font-medium text-blue-900 mb-4">How to Push Images</h3>
        <div class="space-y-4">
          <div>
            <h4 class="font-medium text-blue-800">1. Tag your image</h4>
            <div class="mt-2 bg-blue-100 rounded p-3">
              <code class="text-sm text-blue-900">
                docker tag your-image:latest {{ registryUrl }}/repository-name:tag
              </code>
            </div>
          </div>

          <div>
            <h4 class="font-medium text-blue-800">2. Push to registry</h4>
            <div class="mt-2 bg-blue-100 rounded p-3">
              <code class="text-sm text-blue-900">
                docker push {{ registryUrl }}/repository-name:tag
              </code>
            </div>
          </div>
        </div>
      </div>

      <!-- Registry Configuration -->
      <div class="mt-8 bg-white shadow rounded-lg">
        <div class="px-6 py-4 border-b border-gray-200">
          <h3 class="text-lg font-medium text-gray-900">Registry Configuration</h3>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700">Registry URL</label>
            <div class="mt-1 flex rounded-md shadow-sm">
              <input
                v-model="registryUrl"
                type="text"
                class="flex-1 min-w-0 block w-full px-3 py-2 rounded-md border-gray-300 focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                readonly
              />
              <button
                @click="copyToClipboard(registryUrl)"
                class="inline-flex items-center px-3 py-2 border border-l-0 border-gray-300 rounded-r-md bg-gray-50 text-gray-500 text-sm hover:bg-gray-100"
              >
                <ClipboardIcon class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700">Status</label>
            <div class="mt-1">
              <span
                :class="registryStatus ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
                class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
              >
                <span
                  :class="registryStatus ? 'bg-green-400' : 'bg-red-400'"
                  class="w-1.5 h-1.5 rounded-full mr-1.5"
                ></span>
                {{ registryStatus ? 'Online' : 'Offline' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Example Commands -->
      <div class="mt-8 bg-white shadow rounded-lg">
        <div class="px-6 py-4 border-b border-gray-200">
          <h3 class="text-lg font-medium text-gray-900">Example Commands</h3>
        </div>
        <div class="px-6 py-4">
          <div class="space-y-6">
            <div>
              <h4 class="font-medium text-gray-900 mb-2">Push a simple image</h4>
              <div class="bg-gray-900 rounded-lg p-4 overflow-x-auto">
                <div class="space-y-2 text-sm">
                  <div class="text-gray-300"># Tag your local image</div>
                  <div class="text-green-400">docker tag alpine:latest {{ registryUrl }}/alpine:latest</div>
                  <div class="text-gray-300"># Push to registry</div>
                  <div class="text-green-400">docker push {{ registryUrl }}/alpine:latest</div>
                </div>
              </div>
            </div>

            <div>
              <h4 class="font-medium text-gray-900 mb-2">Build and push from Dockerfile</h4>
              <div class="bg-gray-900 rounded-lg p-4 overflow-x-auto">
                <div class="space-y-2 text-sm">
                  <div class="text-gray-300"># Build image with registry tag</div>
                  <div class="text-green-400">docker build -t {{ registryUrl }}/myapp:v1.0 .</div>
                  <div class="text-gray-300"># Push to registry</div>
                  <div class="text-green-400">docker push {{ registryUrl }}/myapp:v1.0</div>
                </div>
              </div>
            </div>

            <div>
              <h4 class="font-medium text-gray-900 mb-2">Pull from registry</h4>
              <div class="bg-gray-900 rounded-lg p-4 overflow-x-auto">
                <div class="space-y-2 text-sm">
                  <div class="text-gray-300"># Pull image from registry</div>
                  <div class="text-green-400">docker pull {{ registryUrl }}/myapp:v1.0</div>
                  <div class="text-gray-300"># Run the image</div>
                  <div class="text-green-400">docker run {{ registryUrl }}/myapp:v1.0</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Docker Configuration -->
      <div class="mt-8 bg-yellow-50 border border-yellow-200 rounded-lg p-6">
        <h3 class="text-lg font-medium text-yellow-900 mb-4">
          <ExclamationTriangleIcon class="h-5 w-5 inline mr-2" />
          Docker Configuration
        </h3>
        <p class="text-yellow-800 mb-4">
          If your registry is running on HTTP (not HTTPS), you need to configure Docker to allow insecure registries:
        </p>
        <div class="bg-yellow-100 rounded p-3">
          <div class="text-sm text-yellow-900 space-y-2">
            <div>1. Edit or create <code>/etc/docker/daemon.json</code></div>
            <div>2. Add the following configuration:</div>
            <pre class="mt-2 bg-white p-2 rounded text-xs overflow-x-auto">{
  "insecure-registries": ["{{ registryUrl }}"]
}</pre>
            <div>3. Restart Docker daemon: <code>sudo systemctl restart docker</code></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ClipboardIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import registryAPI from '../api/registry'

const registryUrl = ref('localhost:8080')
const registryStatus = ref(false)

onMounted(async () => {
  await checkRegistryStatus()
})

async function checkRegistryStatus() {
  try {
    registryStatus.value = await registryAPI.checkVersion()
  } catch {
    registryStatus.value = false
  }
}

async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    // You could add a toast notification here
  } catch (err) {
    // Fallback for older browsers
    const textArea = document.createElement('textarea')
    textArea.value = text
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
  }
}
</script>
