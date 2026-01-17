<template>
  <div class="admin-container">
    <el-card class="admin-card">
      <template #header>
        <div class="card-header">
          <h2>User Management</h2>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            Create User
          </el-button>
        </div>
      </template>

      <div class="table-container">
        <el-table :data="users" v-loading="loading" style="width: 100%">
          <el-table-column prop="username" label="Username" width="150" />
          <el-table-column prop="email" label="Email" width="200" />
          <el-table-column label="Roles" width="150">
            <template #default="{ row }">
              <el-tag v-for="role in row.roles" :key="role" size="small" style="margin-right: 5px">
                {{ role }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Status" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
                {{ row.enabled ? 'Enabled' : 'Disabled' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="Created" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="250" fixed="right">
            <template #default="{ row }">
              <el-button-group>
                <el-button size="small" @click="editUser(row)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button size="small" @click="managePermissions(row)">
                  <el-icon><Key /></el-icon>
                </el-button>
                <el-button size="small" type="danger" @click="deleteUser(row)" :disabled="row.username === 'admin'">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-button-group>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- Create/Edit User Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      :title="editingUser ? 'Edit User' : 'Create User'"
      width="500px"
    >
      <el-form :model="userForm" :rules="userRules" ref="userFormRef" label-width="100px">
        <el-form-item label="Username" prop="username">
          <el-input v-model="userForm.username" :disabled="!!editingUser" />
        </el-form-item>
        <el-form-item label="Email" prop="email">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="Password" prop="password" v-if="!editingUser">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="Roles" prop="roles">
          <el-select
            v-model="userForm.roles"
            multiple
            placeholder="Select roles"
            style="width: 100%"
          >
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="USER" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="Enabled">
          <el-switch v-model="userForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">Cancel</el-button>
        <el-button type="primary" @click="saveUser" :loading="saving">Save</el-button>
      </template>
    </el-dialog>

    <!-- Permission Management Dialog -->
    <el-dialog
      v-model="showPermissionDialog"
      :title="`Repository Permissions - ${currentUser?.username}`"
      width="600px"
    >
      <div class="permission-controls">
        <el-input
          v-model="newPermission.repository"
          placeholder="Repository name"
          style="width: 200px; margin-right: 10px"
        />
        <el-checkbox v-model="newPermission.canPull">Pull</el-checkbox>
        <el-checkbox v-model="newPermission.canPush" style="margin: 0 10px">Push</el-checkbox>
        <el-button type="primary" @click="addPermission" :icon="Plus">Add</el-button>
      </div>

      <el-table :data="permissions" style="width: 100%; margin-top: 20px">
        <el-table-column prop="repositoryName" label="Repository" />
        <el-table-column label="Permissions" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.canPull" type="success" size="small">Pull</el-tag>
            <el-tag v-if="row.canPush" type="primary" size="small" style="margin-left: 5px">Push</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="deletePermission(row)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminCheck } from '../composables/useAuthCheck'
import { registryApi } from '../api/registry'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Key } from '@element-plus/icons-vue'

const router = useRouter()

const users = ref([])
const loading = ref(false)
const saving = ref(false)
const showCreateDialog = ref(false)
const showPermissionDialog = ref(false)
const editingUser = ref(null)
const currentUser = ref(null)
const permissions = ref([])
const userFormRef = ref()

const userForm = reactive({
  username: '',
  email: '',
  password: '',
  roles: ['USER'],
  enabled: true
})

const newPermission = reactive({
  repository: '',
  canPull: true,
  canPush: false
})

const userRules = {
  username: [
    { required: true, message: 'Please input username', trigger: 'blur' },
    { min: 3, message: 'Username must be at least 3 characters', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: 'Please input correct email address', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please input password', trigger: 'blur' },
    { min: 6, message: 'Password must be at least 6 characters', trigger: 'blur' }
  ]
}

const fetchUsers = async () => {
  loading.value = true
  try {
    users.value = await registryApi.getUsers()
  } catch (error) {
    ElMessage.error('Failed to fetch users: ' + error.message)
  } finally {
    loading.value = false
  }
}

const editUser = (user) => {
  editingUser.value = user
  Object.assign(userForm, {
    username: user.username,
    email: user.email || '',
    password: '',
    roles: user.roles || ['USER'],
    enabled: user.enabled !== false
  })
  showCreateDialog.value = true
}

const saveUser = async () => {
  if (!userFormRef.value) return

  const valid = await userFormRef.value.validate()
  if (!valid) return

  saving.value = true
  try {
    const userData = {
      username: userForm.username,
      email: userForm.email,
      password: userForm.password,
      roles: userForm.roles,
      enabled: userForm.enabled
    }

    if (editingUser.value) {
      delete userData.password
      await registryApi.updateUser(editingUser.value.username, userData)
      ElMessage.success('User updated successfully')
    } else {
      await registryApi.createUser(userData)
      ElMessage.success('User created successfully')
    }

    showCreateDialog.value = false
    fetchUsers()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

const deleteUser = async (user) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete user "${user.username}"?`,
      'Delete User',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )

    await registryApi.deleteUser(user.username)
    ElMessage.success('User deleted successfully')
    fetchUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

const managePermissions = async (user) => {
  currentUser.value = user
  showPermissionDialog.value = true
  await fetchPermissions(user.username)
}

const fetchPermissions = async (username) => {
  try {
    permissions.value = await registryApi.getUserPermissions(username)
  } catch (error) {
    ElMessage.error('Failed to fetch permissions: ' + error.message)
  }
}

const addPermission = async () => {
  if (!newPermission.repository) {
    ElMessage.warning('Please enter repository name')
    return
  }

  try {
    await registryApi.setUserPermission(currentUser.value.username, newPermission.repository, {
      canPull: newPermission.canPull,
      canPush: newPermission.canPush
    })

    ElMessage.success('Permission added successfully')
    newPermission.repository = ''
    newPermission.canPull = true
    newPermission.canPush = false
    await fetchPermissions(currentUser.value.username)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const deletePermission = async (permission) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to remove permission for repository "${permission.repositoryName}"?`,
      'Delete Permission',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )

    await registryApi.deleteUserPermission(currentUser.value.username, permission.repositoryName)
    ElMessage.success('Permission deleted successfully')
    await fetchPermissions(currentUser.value.username)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleString()
}

onMounted(async () => {
  // Wait for auth config to load before checking admin status
  const { checkAdmin } = useAdminCheck()
  const isAdmin = await checkAdmin()

  if (!isAdmin) {
    ElMessage.error('Access denied: Admin privileges required')
    router.push('/')
    return
  }

  fetchUsers()
})
</script>

<style scoped>
.admin-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.admin-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  color: #409EFF;
}

.permission-controls {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.permission-controls .el-checkbox {
  margin-left: 10px;
}
</style>
