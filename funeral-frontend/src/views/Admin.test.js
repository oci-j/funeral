import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Admin from './Admin.vue'
import { elementStubs, iconStubs, loadingDirective } from '../test-utils/element-stubs'

const mockRouter = {
  push: vi.fn(),
}

const checkAdmin = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
}))

vi.mock('../composables/useAuthCheck', () => ({
  useAdminCheck: () => ({ checkAdmin }),
}))

vi.mock('../api/registry', () => ({
  registryApi: {
    getUsers: vi.fn(),
    createUser: vi.fn(),
    updateUser: vi.fn(),
    deleteUser: vi.fn(),
    getUserPermissions: vi.fn(),
    setUserPermission: vi.fn(),
    deleteUserPermission: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

beforeEach(() => {
  vi.clearAllMocks()
  checkAdmin.mockResolvedValue(true)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const createWrapper = () =>
  mount(Admin, {
    global: {
      stubs: { ...elementStubs, ...iconStubs },
      directives: { loading: loadingDirective },
    },
  })

const mockUsers = [
  {
    username: 'admin',
    email: 'admin@example.com',
    roles: ['ADMIN'],
    enabled: true,
    createdAt: '2026-01-01',
  },
  {
    username: 'user',
    email: 'user@example.com',
    roles: ['USER'],
    enabled: false,
    createdAt: '2026-01-02',
  },
]

describe('Admin', () => {
  it('redirects non-admin users to home', async () => {
    checkAdmin.mockResolvedValue(false)
    const { ElMessage } = await import('element-plus')

    createWrapper()
    await flushPromises()

    expect(mockRouter.push).toHaveBeenCalledWith('/')
    expect(ElMessage.error).toHaveBeenCalledWith('Access denied: Admin privileges required')
  })

  it('fetches users on mount', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getUsers.mockResolvedValue(mockUsers)

    const wrapper = createWrapper()
    await flushPromises()

    expect(registryApi.getUsers).toHaveBeenCalled()
    expect(wrapper.find('.admin-card').exists()).toBe(true)
    expect(wrapper.vm.users).toEqual(mockUsers)
  })

  it('opens create user dialog', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getUsers.mockResolvedValue(mockUsers)

    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.showCreateDialog = true
    await flushPromises()

    expect(wrapper.find('.el-dialog').exists()).toBe(true)
    expect(wrapper.find('.el-dialog__title').text()).toBe('Create User')
  })

  it('creates a new user', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.createUser.mockResolvedValue({})

    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.showCreateDialog = true
    wrapper.vm.userForm.username = 'newuser'
    wrapper.vm.userForm.email = 'new@example.com'
    wrapper.vm.userForm.password = 'password123'
    wrapper.vm.userForm.roles = ['USER']
    await flushPromises()

    await wrapper.findAll('.el-dialog__footer .el-button').at(1).trigger('click')
    await flushPromises()

    expect(registryApi.createUser).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('User created successfully')
  })

  it('edits an existing user', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.updateUser.mockResolvedValue({})

    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.editUser(mockUsers[0])
    await flushPromises()

    expect(wrapper.find('.el-dialog__title').text()).toBe('Edit User')
    expect(wrapper.vm.userForm.username).toBe('admin')

    wrapper.vm.userForm.email = 'admin2@example.com'
    await wrapper.findAll('.el-dialog__footer .el-button').at(1).trigger('click')
    await flushPromises()

    expect(registryApi.updateUser).toHaveBeenCalledWith('admin', expect.any(Object))
    expect(ElMessage.success).toHaveBeenCalledWith('User updated successfully')
  })

  it('deletes a user after confirmation', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage, ElMessageBox } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.deleteUser.mockResolvedValue({})
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.deleteUser(mockUsers[1])
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(registryApi.deleteUser).toHaveBeenCalledWith('user')
    expect(ElMessage.success).toHaveBeenCalledWith('User deleted successfully')
  })

  it('shows error when fetching users fails', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getUsers.mockRejectedValue(new Error('network'))

    const wrapper = createWrapper()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Failed to fetch users: network')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('shows error when creating user fails', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.createUser.mockRejectedValue(new Error('username taken'))

    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.showCreateDialog = true
    wrapper.vm.userForm.username = 'newuser'
    wrapper.vm.userForm.email = 'new@example.com'
    wrapper.vm.userForm.password = 'password123'
    wrapper.vm.userForm.roles = ['USER']
    await flushPromises()

    await wrapper.findAll('.el-dialog__footer .el-button').at(1).trigger('click')
    await flushPromises()

    expect(registryApi.createUser).toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('username taken')
    expect(wrapper.vm.saving).toBe(false)
  })

  it('does not delete user on cancel', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessageBox, ElMessage } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.deleteUser(mockUsers[1])
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(registryApi.deleteUser).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('shows error when deleting user fails', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage, ElMessageBox } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.deleteUser.mockRejectedValue(new Error('forbidden'))
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.deleteUser(mockUsers[1])
    await flushPromises()

    expect(registryApi.deleteUser).toHaveBeenCalledWith('user')
    expect(ElMessage.error).toHaveBeenCalledWith('forbidden')
  })

  it('shows error when fetching permissions fails', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.getUserPermissions.mockRejectedValue(new Error('unauthorized'))

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.managePermissions(mockUsers[0])
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Failed to fetch permissions: unauthorized')
  })

  it('warns when adding permission without repository', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage, ElMessageBox } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.getUserPermissions.mockResolvedValue([])
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.managePermissions(mockUsers[0])
    await flushPromises()

    await wrapper.find('.permission-controls .el-button').trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('Please enter repository name')
    expect(registryApi.setUserPermission).not.toHaveBeenCalled()
  })

  it('shows error when adding permission fails', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage, ElMessageBox } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.getUserPermissions.mockResolvedValue([])
    registryApi.setUserPermission.mockRejectedValue(new Error('denied'))
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.managePermissions(mockUsers[0])
    await flushPromises()

    wrapper.vm.newPermission.repository = 'repo/one'
    await wrapper.find('.permission-controls .el-button').trigger('click')
    await flushPromises()

    expect(registryApi.setUserPermission).toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('denied')
  })

  it('does not delete permission on cancel', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessageBox, ElMessage } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.getUserPermissions.mockResolvedValue([
      { repositoryName: 'repo/one', canPull: true, canPush: false },
    ])
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.managePermissions(mockUsers[0])
    await flushPromises()

    await wrapper.vm.deletePermission({ repositoryName: 'repo/one' })
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(registryApi.deleteUserPermission).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('shows error when deleting permission fails', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage, ElMessageBox } = await import('element-plus')
    registryApi.getUsers.mockResolvedValue(mockUsers)
    registryApi.getUserPermissions.mockResolvedValue([
      { repositoryName: 'repo/one', canPull: true, canPush: false },
    ])
    registryApi.deleteUserPermission.mockRejectedValue(new Error('denied'))
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.managePermissions(mockUsers[0])
    await flushPromises()

    await wrapper.vm.deletePermission({ repositoryName: 'repo/one' })
    await flushPromises()

    expect(registryApi.deleteUserPermission).toHaveBeenCalledWith('admin', 'repo/one')
    expect(ElMessage.error).toHaveBeenCalledWith('denied')
  })

  it('formatDate returns empty for falsy values', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatDate('')).toBe('')
    expect(wrapper.vm.formatDate(null)).toBe('')
    expect(wrapper.vm.formatDate(undefined)).toBe('')
  })
})
