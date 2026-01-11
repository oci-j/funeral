<template>
  <div class="home-container">
    <div class="page-header">
      <h1>Repositories</h1>
      <el-button type="primary" @click="refreshRepositories">
        <el-icon><RefreshRight /></el-icon>
        Refresh
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="repositories"
      style="width: 100%"
      border
    >
      <el-table-column prop="name" label="Name" width="300">
        <template #default="scope">
          <el-link
            type="primary"
            @click="viewRepository(scope.row.name)"
          >
            {{ scope.row.name }}
          </el-link>
        </template>
      </el-table-column>

      <el-table-column prop="tagCount" label="Tag Count" width="120" align="center" />
      <el-table-column prop="createdAt" label="Created" width="180">
        <template #default="scope">
          {{ formatDate(scope.row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="120">
        <template #default="scope">
          <el-button
            type="danger"
            size="small"
            @click="handleDeleteRepository(scope.row.name)"
          >
            Delete
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && repositories.length === 0"
      description="No repositories found"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshRight } from '@element-plus/icons-vue'
import { registryApi } from '../api/registry'
import { useProtectedPage } from '../composables/useAuthCheck'

const router = useRouter()
const repositories = ref([])
const loading = ref(false)

const fetchRepositories = async () => {
  loading.value = true
  try {
    const data = await registryApi.getRepositories()
    // Backend returns an array of RepositoryInfo objects directly
    repositories.value = data || []
    console.log('Repositories data:', data)
  } catch (error) {
    ElMessage.error('Failed to fetch repositories')
    console.error('Error fetching repositories:', error)
  } finally {
    loading.value = false
  }
}

const refreshRepositories = () => {
  fetchRepositories()
}

const formatDate = (dateString) => {
  if (!dateString) return 'Unknown'
  try {
    const date = new Date(dateString)
    return date.toLocaleString()
  } catch (error) {
    return dateString
  }
}

const viewRepository = (name) => {
  // Encode the repository name to handle slashes and special characters
  const encodedName = encodeURIComponent(name)
  router.push(`/repository/${encodedName}`)
}

const handleDeleteRepository = async (name) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete repository "${name}"?`,
      'Warning',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    loading.value = true
    await registryApi.deleteRepository(name)
    ElMessage.success('Repository deleted successfully')
    await fetchRepositories()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to delete repository')
    }
  } finally {
    loading.value = false
  }
}

// Initialize page with auth check
const { initPage } = useProtectedPage(router, fetchRepositories, { loading })
onMounted(() => {
  initPage()
})
</script>

<style scoped>
.home-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
}
</style>
