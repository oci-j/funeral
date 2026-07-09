import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { elementStubs, iconStubs, loadingDirective } from './test-utils/element-stubs'

const mockRouter = {
  push: vi.fn(),
}

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
}))

vi.mock('./stores/auth', () => ({
  useAuthStore: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

const AboutDialogStub = {
  name: 'AboutDialog',
  setup() {
    const open = vi.fn()
    return { open }
  },
  template: '<div class="about-dialog-stub" />',
}

const createAuthStore = (overrides = {}) => ({
  isAuthenticated: false,
  authEnabled: true,
  isAdmin: false,
  user: null,
  checkingConfig: false,
  logout: vi.fn(),
  ...overrides,
})

const createWrapper = async (options = {}) => {
  const { authStore = createAuthStore(), routePath = '/' } = options
  const { useAuthStore } = await import('./stores/auth')
  useAuthStore.mockReturnValue(authStore)

  const { default: App } = await import('./App.vue')

  return mount(App, {
    global: {
      stubs: { ...elementStubs, ...iconStubs, AboutDialog: AboutDialogStub, 'router-view': {
        name: 'RouterView',
        template: '<div class="router-view-stub"><slot /></div>',
      } },
      mocks: {
        $route: { path: routePath },
        $router: mockRouter,
      },
      directives: { loading: loadingDirective },
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('App', () => {
  it('renders main layout for non-login routes', async () => {
    const wrapper = await createWrapper()

    expect(wrapper.find('.el-header').exists()).toBe(true)
    expect(wrapper.find('.sidebar').exists()).toBe(true)
    expect(wrapper.find('.router-view-stub').exists()).toBe(true)
    expect(wrapper.find('.about-dialog-stub').exists()).toBe(true)
  })

  it('renders login page without layout for /login', async () => {
    const wrapper = await createWrapper({ routePath: '/login' })

    expect(wrapper.find('.el-header').exists()).toBe(false)
    expect(wrapper.find('.sidebar').exists()).toBe(false)
    expect(wrapper.find('.router-view-stub').exists()).toBe(true)
  })

  it('shows login button when not authenticated', async () => {
    const wrapper = await createWrapper()

    const loginBtn = wrapper.find('.header-actions .el-button')
    expect(loginBtn.text()).toContain('Login')
  })

  it('shows user menu when authenticated', async () => {
    const wrapper = await createWrapper({
      authStore: createAuthStore({ isAuthenticated: true, user: { username: 'admin' } }),
    })

    expect(wrapper.find('.user-dropdown').text()).toContain('admin')
  })

  it('shows admin menu item for admin users', async () => {
    const wrapper = await createWrapper({
      authStore: createAuthStore({ isAdmin: true }),
    })

    const items = wrapper.findAll('.el-menu-item').map(item => item.text())
    expect(items).toContain('Admin')
  })

  it('hides admin menu item for non-admin users', async () => {
    const wrapper = await createWrapper({ authStore: createAuthStore({ isAdmin: false }) })

    const items = wrapper.findAll('.el-menu-item').map(item => item.text())
    expect(items).not.toContain('Admin')
  })

  it('handles logout from user dropdown', async () => {
    const { ElMessage } = await import('element-plus')
    const authStore = createAuthStore({ isAuthenticated: true, user: { username: 'admin' } })
    const wrapper = await createWrapper({ authStore })

    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    await dropdown.vm.$emit('command', 'logout')
    await flushPromises()

    expect(authStore.logout).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('Logged out successfully')
    expect(mockRouter.push).toHaveBeenCalledWith('/login')
  })

  it('toggles mobile menu when menu button is clicked', async () => {
    const wrapper = await createWrapper()

    expect(wrapper.vm.mobileMenuOpen).toBe(false)
    await wrapper.find('.menu-toggle').trigger('click')
    expect(wrapper.vm.mobileMenuOpen).toBe(true)
  })

  it('opens about dialog when info button is clicked', async () => {
    const wrapper = await createWrapper()

    const infoBtn = wrapper.find('.logo-section .el-button.hide-xs')
    expect(infoBtn.exists()).toBe(true)
    await infoBtn.trigger('click')

    const aboutDialog = wrapper.findComponent(AboutDialogStub)
    expect(aboutDialog.vm.open).toHaveBeenCalled()
  })
})
