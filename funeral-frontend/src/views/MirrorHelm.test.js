import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import MirrorHelm from './MirrorHelm.vue'
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
  mount(MirrorHelm, {
    global: {
      stubs: { ...elementStubs, ...iconStubs },
      directives: { loading: loadingDirective },
    },
  })

const successResponse = {
  source: 'oci-registry',
  chart: 'nginx',
  version: '1.0.0',
  targetChart: 'nginx',
  targetVersion: '1.0.0',
  format: 'oci',
  digest: 'sha256:helm',
}

describe('MirrorHelm', () => {
  it('renders helm mirror form', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.mirror-helm-form').exists()).toBe(true)
  })

  it('switches format and clears source repo', async () => {
    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'

    await wrapper.find('.el-select').setValue('chartmuseum')
    await flushPromises()

    expect(wrapper.vm.form.format).toBe('chartmuseum')
    expect(wrapper.vm.form.sourceRepo).toBe('')
  })

  it('starts helm mirroring and shows result on success', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    wrapper.vm.form.version = '1.0.0'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith(
      '/funeral_addition/mirror/helm/pull',
      expect.objectContaining({ method: 'POST' })
    )
    expect(wrapper.find('.result-card').exists()).toBe(true)
    expect(ElMessage.success).toHaveBeenCalledWith('Successfully mirrored nginx!')
  })

  it('shows error result on failure', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      statusText: 'Bad Request',
      json: async () => ({ errors: [{ message: 'chart not found' }] }),
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.result-error').exists()).toBe(true)
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('logs out on 401 response', async () => {
    global.fetch.mockResolvedValue({
      status: 401,
      ok: false,
      statusText: 'Unauthorized',
    })

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    expect(mockAuthStore.logout).toHaveBeenCalled()
  })

  it('copies helm install command to clipboard', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    const copyBtn = wrapper
      .findAll('.result-actions .el-button')
      .find(btn => btn.text().includes('Copy'))
    expect(copyBtn).toBeDefined()
    await copyBtn.trigger('click')
    await flushPromises()

    expect(navigator.clipboard.writeText).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('Helm command copied to clipboard')
  })

  it('shows error when required fields are empty', async () => {
    const { ElMessage } = await import('element-plus')
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.startMirroring()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Please complete required fields')
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('defaults target repository to chart name and target version to version', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    wrapper.vm.form.version = '1.0.0'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    const body = global.fetch.mock.calls[0][1].body.toString()
    expect(body).toContain('targetRepository=nginx')
    expect(body).toContain('targetVersion=1.0.0')
  })

  it('generates ChartMuseum install command', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        ...successResponse,
        format: 'chartmuseum',
        targetChart: 'nginx',
        targetVersion: '1.0.0',
      }),
    })

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'https://charts.example.com'
    wrapper.vm.form.chartName = 'nginx'
    wrapper.vm.form.format = 'chartmuseum'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    const copyBtn = wrapper
      .findAll('.result-actions .el-button')
      .find(btn => btn.text().includes('Copy'))
    expect(copyBtn).toBeDefined()
    await copyBtn.trigger('click')
    await flushPromises()

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      expect.stringContaining('helm repo add')
    )
  })

  it('shows fallback error when response has no error details', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      statusText: 'Bad Request',
      json: async () => ({}),
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.result-error').exists()).toBe(true)
    expect(ElMessage.error).toHaveBeenCalledWith('Mirror failed: Bad Request')
  })

  it('shows error when copying helm command fails', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })
    navigator.clipboard.writeText.mockRejectedValue(new Error('denied'))
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    const copyBtn = wrapper
      .findAll('.result-actions .el-button')
      .find(btn => btn.text().includes('Copy'))
    expect(copyBtn).toBeDefined()
    await copyBtn.trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Failed to copy to clipboard')
  })

  it('navigates to repository on success', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })

    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'registry.example.com'
    wrapper.vm.form.chartName = 'nginx'
    await flushPromises()

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    const viewBtn = wrapper
      .findAll('.result-actions .el-button')
      .find(btn => btn.text().includes('View Repository'))
    expect(viewBtn).toBeDefined()
    await viewBtn.trigger('click')

    expect(mockRouter.push).toHaveBeenCalledWith('/repository/nginx')
  })

  it('fills all form fields via the DOM and starts mirroring', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => successResponse,
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.find('.mirror-helm-form .el-select').setValue('chartmuseum')
    const inputs = wrapper.findAll('.mirror-helm-form .el-input')
    await inputs.at(0).setValue('https://charts.example.com')
    await inputs.at(1).setValue('nginx')
    await inputs.at(2).setValue('1.0.0')
    await inputs.at(3).setValue('my-nginx')
    await inputs.at(4).setValue('1.0.1')
    await inputs.at(5).setValue('user')
    await inputs.at(6).setValue('pass')

    await wrapper.find('.mirror-helm-actions .el-button').trigger('click')
    await flushPromises()

    const body = global.fetch.mock.calls[0][1].body.toString()
    expect(body).toContain('sourceRepo=https%3A%2F%2Fcharts.example.com')
    expect(body).toContain('chartName=nginx')
    expect(body).toContain('version=1.0.0')
    expect(body).toContain('targetRepository=my-nginx')
    expect(body).toContain('targetVersion=1.0.1')
    expect(body).toContain('format=chartmuseum')
    expect(body).toContain('username=user')
    expect(body).toContain('password=pass')
    expect(ElMessage.success).toHaveBeenCalledWith('Successfully mirrored nginx!')
  })

  it('resets the form by clicking Reset', async () => {
    const wrapper = createWrapper()
    wrapper.vm.form.sourceRepo = 'https://charts.example.com'
    wrapper.vm.form.chartName = 'nginx'
    wrapper.vm.form.format = 'chartmuseum'
    wrapper.vm.form.version = '1.0.0'
    await flushPromises()

    await wrapper.findAll('.mirror-helm-actions .el-button').at(1).trigger('click')
    await flushPromises()

    expect(wrapper.vm.form.sourceRepo).toBe('')
    expect(wrapper.vm.form.chartName).toBe('')
    expect(wrapper.vm.form.format).toBe('oci')
    expect(wrapper.vm.form.version).toBe('')
  })
})
