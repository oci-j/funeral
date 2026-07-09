import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TagDetail from './TagDetail.vue'
import { elementStubs, iconStubs, loadingDirective } from '../test-utils/element-stubs'

const mockRouter = {
  push: vi.fn(),
  back: vi.fn(),
}

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
  useRoute: () => ({ params: { name: 'repo%2Fone', tag: 'v1.0' } }),
}))

vi.mock('../composables/useAuthCheck', () => ({
  useProtectedPage: (router, fetchData) => ({
    initPage: async () => {
      if (fetchData) await fetchData()
    },
  }),
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({}),
}))

vi.mock('../api/registry', () => ({
  registryApi: {
    getManifest: vi.fn(),
    getManifestInfo: vi.fn(),
    getBlobContent: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('vue-json-pretty', () => ({
  default: { name: 'VueJsonPretty', template: '<div class="vue-json-pretty-stub" />' },
}))
vi.mock('vue-json-pretty/lib/styles.css', () => ({}))

const TarViewerStub = {
  name: 'TarViewer',
  props: ['arrayBuffer', 'mediaType'],
  template: '<div class="tar-viewer-stub">Tar Viewer</div>',
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('location', { hostname: 'localhost', port: '8911' })
  vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const createWrapper = () =>
  mount(TagDetail, {
    props: { name: 'repo/one', tag: 'v1.0' },
    global: {
      stubs: { ...elementStubs, ...iconStubs, TarViewer: TarViewerStub },
      directives: { loading: loadingDirective },
    },
  })

const mockManifest = (overrides = {}) => ({
  config: {
    digest: 'sha256:config',
    size: 512,
    mediaType: 'application/vnd.oci.image.config.v1+json',
  },
  layers: [
    {
      digest: 'sha256:layer1',
      size: 1024,
      mediaType: 'application/vnd.oci.image.layer.v1.tar+gzip',
    },
  ],
  ...overrides,
})

const mockManifestInfo = () => ({
  digest: 'sha256:abc',
  size: 2048,
  created: '2026-01-01',
  mediaType: 'application/vnd.oci.image.manifest.v1+json',
})

describe('TagDetail', () => {
  it('fetches and renders tag details on mount', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getManifest.mockResolvedValue(mockManifest())
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())

    const wrapper = createWrapper()
    await flushPromises()

    expect(registryApi.getManifest).toHaveBeenCalledWith('repo/one', 'v1.0')
    expect(wrapper.find('.tag-info-card').text()).toContain('repo/one:v1.0')
    expect(wrapper.find('.config-card').exists()).toBe(true)
    expect(wrapper.find('.layers-card').exists()).toBe(true)
  })

  it('detects Helm chart and shows helm commands', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getManifest.mockResolvedValue(
      mockManifest({
        config: {
          digest: 'sha256:config',
          size: 512,
          mediaType: 'application/vnd.cncf.helm.config.v1+json',
        },
      })
    )
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.find('.tag-info-card').text()).toContain('Helm Chart')
    expect(wrapper.find('.command-input').element.querySelector('input').value).toContain('helm pull')
  })

  it('detects Docker image', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getManifest.mockResolvedValue(mockManifest())
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.find('.tag-info-card').text()).toContain('Docker Image')
  })

  it('shows manifest details when Details is clicked', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getManifest.mockResolvedValue(mockManifest())
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())

    const wrapper = createWrapper()
    await flushPromises()

    const detailBtn = wrapper.findAll('.tag-info-card .el-button').find(function (btn) {
      return btn.text().includes('Details')
    })
    expect(detailBtn).toBeDefined()
    await detailBtn.trigger('click')
    await flushPromises()

    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.find('.vue-json-pretty').exists()).toBe(true)
    expect(wrapper.vm.blobContent.content).toContain('sha256:config')
  })

  it('shows blob content for a layer', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getManifest.mockResolvedValue(mockManifest())
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())
    registryApi.getBlobContent.mockResolvedValue({
      type: 'text',
      content: '{"layer":"data"}',
      contentType: 'application/json',
    })

    const wrapper = createWrapper()
    await flushPromises()

    const layerBtn = wrapper.findAll('.layer-card .el-button').find(function (btn) {
      return btn.text().includes('Details')
    })
    expect(layerBtn).toBeDefined()
    await layerBtn.trigger('click')
    await flushPromises()

    expect(registryApi.getBlobContent).toHaveBeenCalledWith('repo/one', 'sha256:layer1', expect.any(String))
    expect(wrapper.find('.vue-json-pretty').exists()).toBe(true)
    expect(wrapper.vm.blobContent.content).toContain('"layer":"data"')
  })

  it('shows TarViewer for tar layer blobs', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getManifest.mockResolvedValue(mockManifest())
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())
    registryApi.getBlobContent.mockResolvedValue({
      type: 'blob',
      mediaType: 'application/vnd.oci.image.layer.v1.tar+gzip',
      arrayBuffer: new ArrayBuffer(8),
    })

    const wrapper = createWrapper()
    await flushPromises()

    const layerBtn = wrapper.findAll('.layer-card .el-button').find(function (btn) {
      return btn.text().includes('Details')
    })
    await layerBtn.trigger('click')
    await flushPromises()

    expect(wrapper.find('.tar-viewer-stub').exists()).toBe(true)
  })

  it('copies pull command to clipboard', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getManifest.mockResolvedValue(mockManifest())
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())

    const wrapper = createWrapper()
    await flushPromises()

    const copyBtn = wrapper.find('.command-input .el-button')
    await copyBtn.trigger('click')
    await flushPromises()

    expect(navigator.clipboard.writeText).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('Copied to clipboard')
  })

  it('goes back when Back is clicked', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getManifest.mockResolvedValue(mockManifest())
    registryApi.getManifestInfo.mockResolvedValue(mockManifestInfo())

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.find('.page-header .el-button').trigger('click')
    expect(mockRouter.back).toHaveBeenCalled()
  })

  it('handles fetch error gracefully', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getManifest.mockRejectedValue(new Error('network'))

    const wrapper = createWrapper()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Failed to fetch tag details: network')
    expect(wrapper.vm.loading).toBe(false)
  })
})
