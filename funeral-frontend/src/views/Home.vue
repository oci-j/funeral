<template>
  <CommonPageLayout
    title="Repositories"
    :loading="loading"
    :empty="repositories.length === 0"
    empty-text="No repositories found"
    :items="repositories"
    :show-about-button="false"
  >
    <template #actions>
      <el-button type="primary" @click="refreshRepositories">
        <el-icon><RefreshRight /></el-icon>
        Refresh
      </el-button>
    </template>

    <!-- Desktop view - Table -->
    <div class="hide-md">
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
              <el-icon><Delete /></el-icon>
              Delete
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Mobile view - Cards -->
    <div class="show-md" style="display: none;">
      <div class="repository-cards">
        <el-card
          v-for="repo in repositories"
          :key="repo.name"
          class="repository-card"
          shadow="hover"
        >
          <div class="repository-card-content">
            <div class="repository-header">
              <el-link
                type="primary"
                class="repository-name"
                @click="viewRepository(repo.name)"
              >
                {{ repo.name }}
              </el-link>
              <el-tag size="small" type="info">
                {{ repo.tagCount || 0 }} tags
              </el-tag>
            </div>
            <div class="repository-info">
              <div class="info-item">
                <span class="info-label">Created:</span>
                <span class="info-value">{{ formatDate(repo.createdAt) }}</span>
              </div>
            </div>
            <div class="repository-actions">
              <el-button
                type="danger"
                size="small"
                @click="handleDeleteRepository(repo.name)"
              >
                <el-icon><Delete /></el-icon>
                Delete
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </CommonPageLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshRight, Delete } from '@element-plus/icons-vue'
import { registryApi } from '../api/registry'
import { useProtectedPage } from '../composables/useAuthCheck'
import { formatDate } from '../utils/common'
import CommonPageLayout from '../components/CommonPageLayout.vue'

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
/* Repository Cards for Mobile */
.repository-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.repository-card {
  transition: all 0.3s ease;
}

.repository-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.repository-card-content {
  padding: 8px;
}

.repository-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.repository-name {
  font-weight: 600;
  font-size: 16px;
  word-break: break-all;
}

.repository-info {
  margin-bottom: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.info-label {
  color: #606266;
  font-size: 14px;
}

.info-value {
  color: #303133;
  font-weight: 500;
  font-size: 14px;
}

.repository-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 576px) {
  .repository-card-content {
    padding: 6px;
  }

  .repository-name {
    font-size: 14px;
  }

  .repository-header,
  .info-item {
    gap: 8px;
  }

  .repository-actions .el-button {
    padding: 6px 12px;
  }
}
</style>
