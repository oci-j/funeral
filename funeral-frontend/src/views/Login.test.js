import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Login from './Login.vue'
import { elementStubs, loadingDirective } from '../test-utils/element-stubs'

const mockRouter = {
  push: vi.fn(),
}

const mockRoute = {
  query: {},
}

const mockAuthStore = {
  login: vi.fn(),
  loginAnonymous: vi.fn(),
}

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
  useRoute: () => mockRoute,
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

beforeEach(() => {
  vi.clearAllMocks()
  globalThis.ElMessage = { success: vi.fn(), error: vi.fn() }
})

const createWrapper = () =>
  mount(Login, {
    global: {
      stubs: elementStubs,
      directives: { loading: loadingDirective },
    },
  })

describe('Login', () => {
  it('renders the login form with default credentials', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.login-header h2').text()).toBe('FUNERAL Registry Login')
    const inputs = wrapper.findAll('.el-input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)
  })

  it('logs in with username and password', async () => {
    mockAuthStore.login.mockResolvedValue({ success: true })
    const wrapper = createWrapper()

    wrapper.vm.loginForm.username = 'admin'
    wrapper.vm.loginForm.password = 'password'
    await wrapper.find('.login-button').trigger('click')
    await flushPromises()

    expect(mockAuthStore.login).toHaveBeenCalledWith('admin', 'password')
    expect(mockRouter.push).toHaveBeenCalledWith('/')
    expect(ElMessage.success).toHaveBeenCalledWith('Login successful')
  })

  it('redirects to intended page after login', async () => {
    mockRoute.query = { redirect: '/admin' }
    mockAuthStore.login.mockResolvedValue({ success: true })
    const wrapper = createWrapper()

    await wrapper.find('.login-button').trigger('click')
    await flushPromises()

    expect(mockRouter.push).toHaveBeenCalledWith('/admin')
    mockRoute.query = {}
  })

  it('shows error message on login failure', async () => {
    mockAuthStore.login.mockResolvedValue({ success: false, error: 'Invalid credentials' })
    const wrapper = createWrapper()

    await wrapper.find('.login-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.error-message').exists()).toBe(true)
    expect(wrapper.find('.error-message').text()).toContain('Invalid credentials')
  })

  it('logs in as anonymous', async () => {
    mockAuthStore.loginAnonymous.mockResolvedValue({ success: true })
    const wrapper = createWrapper()

    await wrapper.find('.el-checkbox input').setValue(true)
    await flushPromises()

    expect(wrapper.findAll('.el-input').length).toBe(0)
    await wrapper.find('.login-button').trigger('click')
    await flushPromises()

    expect(mockAuthStore.loginAnonymous).toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith('/')
  })

  it('fills credentials through the DOM and logs in', async () => {
    mockAuthStore.login.mockResolvedValue({ success: true })
    const wrapper = createWrapper()

    const inputs = wrapper.findAll('.el-input')
    await inputs.at(0).setValue('admin')
    await inputs.at(1).setValue('password')
    await wrapper.find('.login-button').trigger('click')
    await flushPromises()

    expect(mockAuthStore.login).toHaveBeenCalledWith('admin', 'password')
    expect(mockRouter.push).toHaveBeenCalledWith('/')
  })

  it('submits the form on enter key', async () => {
    mockAuthStore.login.mockResolvedValue({ success: true })
    const wrapper = createWrapper()

    const input = wrapper.find('.el-input')
    await input.setValue('admin')
    await input.trigger('keyup', { key: 'Enter' })
    await flushPromises()

    expect(mockAuthStore.login).toHaveBeenCalledWith('admin', expect.any(String))
  })

  it('shows error when login throws an exception', async () => {
    mockAuthStore.login.mockRejectedValue(new Error('network'))
    const wrapper = createWrapper()

    await wrapper.find('.login-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.error-message').exists()).toBe(true)
    expect(wrapper.find('.error-message').text()).toContain('network')
  })
})
