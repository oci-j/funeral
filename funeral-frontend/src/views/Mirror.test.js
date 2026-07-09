import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Mirror from './Mirror.vue'
import { elementStubs, iconStubs, loadingDirective } from '../test-utils/element-stubs'

const mockRouter = {
  push: vi.fn(),
}

const mockAuthStore = {
  getAuthHeader: vi.fn(() => 'Bearer token'),
  logout: vi.fn(),
}

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('fetch', vi.fn())
  vi.stubGlobal('location', { hostname: 'localhost', port: '8911' })
  vi.stubGlobal('navigator', {
    clipboard: { writeText: vi.fn() },
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const createWrapper = () =>
  mount(Mirror, {
    global: {
      stubs: { ...elementStubs, ...iconStubs },
      directives: { loading: loadingDirective },
    },
  })

const successResponse = {
  sourceImage: 'nginx:latest',
  targetRepository: 'nginx',
  targetTag: 'latest',
  manifestDigest: 'sha256:abc',
  blobsCount: 3,
}

describe('Mirror', () => {
  it('renders mirror form', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.mirror-form').exists()).toBe(true)
    expect(wrapper.findAll('.command-input').length).toBe(0)
  })

  it('starts mirroring and shows result on success', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceImage = 'nginx:latest'
    await flushPromises()

    await wrapper.find('.mirror-actions .el-button').trigger('click')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith(
      '/funeral_addition/mirror/pull',
      expect.objectContaining({ method: 'POST' })
    )
    expect(wrapper.find('.result-card').exists()).toBe(true)
    expect(ElMessage.success).toHaveBeenCalledWith('Successfully mirrored nginx:latest!')
  })

  it('shows error result on mirror failure', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      statusText: 'Bad Request',
      json: async () => ({ errors: [{ message: 'invalid image' }] }),
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceImage = 'bad:image'
    await flushPromises()

    await wrapper.find('.mirror-actions .el-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.result-card').exists()).toBe(true)
    expect(wrapper.find('.result-error').exists()).toBe(true)
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('logs out on 401 response', async () => {
    global.fetch.mockResolvedValue({
      status: 401,
      ok: false,
      statusText: 'Unauthorized',
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceImage = 'nginx:latest'
    await flushPromises()

    await wrapper.find('.mirror-actions .el-button').trigger('click')
    await flushPromises()

    expect(mockAuthStore.logout).toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('resets form', async () => {
    const wrapper = createWrapper()
    wrapper.vm.form.sourceImage = 'nginx:latest'
    wrapper.vm.form.targetRepository = 'nginx'
    wrapper.vm.form.targetTag = 'latest'
    await flushPromises()

    await wrapper.findAll('.mirror-actions .el-button').at(1).trigger('click')
    await flushPromises()

    expect(wrapper.vm.form.sourceImage).toBe('')
    expect(wrapper.vm.form.targetRepository).toBe('')
    expect(wrapper.vm.form.targetTag).toBe('')
    expect(wrapper.vm.result).toBeNull()
  })

  it('copies pull command to clipboard', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceImage = 'nginx:latest'
    await flushPromises()

    await wrapper.find('.mirror-actions .el-button').trigger('click')
    await flushPromises()

    const copyBtn = wrapper
      .findAll('.result-actions .el-button')
      .find(btn => btn.text().includes('Copy'))
    expect(copyBtn).toBeDefined()
    await copyBtn.trigger('click')
    await flushPromises()

    expect(navigator.clipboard.writeText).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('Pull command copied to clipboard')
  })
})
