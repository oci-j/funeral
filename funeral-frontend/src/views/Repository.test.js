import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Repository from './Repository.vue'
import { elementStubs, iconStubs, loadingDirective } from '../test-utils/element-stubs'

const mockRouter = {
  push: vi.fn(),
  back: vi.fn(),
}

const mockRoute = {
  params: { name: 'repo%2Fone' },
}

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
  useRoute: () => mockRoute,
}))

vi.mock('../composables/useAuthCheck', () => ({
  useProtectedPage: (router, fetchData) => ({
    initPage: async () => {
      if (fetchData) await fetchData()
    },
  }),
}))

vi.mock('../api/registry', () => ({
  registryApi: {
    getRepositoryTags: vi.fn(),
    getManifestInfo: vi.fn(),
    getManifest: vi.fn(),
    deleteTag: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('location', { hostname: 'localhost', port: '8911' })
  vi.stubGlobal('navigator', {
    clipboard: { writeText: vi.fn() },
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const createWrapper = () =>
  mount(Repository, {
    global: {
      stubs: { ...elementStubs, ...iconStubs },
      directives: { loading: loadingDirective },
    },
  })

describe('Repository', () => {
  it('fetches and renders tags on mount', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessageBox } = await import('element-plus')
    registryApi.getRepositoryTags.mockResolvedValue({ tags: ['v1.0'] })
    registryApi.getManifestInfo.mockResolvedValue({
      digest: 'sha256:abc',
      contentLength: 1024,
      createdAt: '2026-01-01',
    })
    registryApi.getManifest.mockResolvedValue({
      config: { mediaType: 'application/vnd.oci.image.config.v1+json' },
      layers: [],
    })
    ElMessageBox.confirm.mockResolvedValue('cancel')

    const wrapper = createWrapper()
    await flushPromises()

    expect(registryApi.getRepositoryTags).toHaveBeenCalledWith('repo/one')
    expect(wrapper.find('.tag-card').exists()).toBe(true)
    expect(wrapper.find('.tag-name').text()).toBe('v1.0')
    expect(wrapper.find('.digest-text').text()).toContain('sha256:abc')
  })

  it('detects Helm chart type and shows helm pull command', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositoryTags.mockResolvedValue({ tags: ['1.0'] })
    registryApi.getManifestInfo.mockResolvedValue({
      digest: 'sha256:helm',
      contentLength: 2048,
      createdAt: '2026-01-01',
    })
    registryApi.getManifest.mockResolvedValue({
      config: { mediaType: 'application/vnd.cncf.helm.config.v1+json' },
      layers: [],
    })

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.find('.el-tag').text()).toBe('Helm Chart')
    expect(wrapper.find('.command-input').element.querySelector('input').value).toContain(
      'helm pull'
    )
  })

  it('detects Docker image type', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositoryTags.mockResolvedValue({ tags: ['latest'] })
    registryApi.getManifestInfo.mockResolvedValue({
      digest: 'sha256:docker',
      contentLength: 1024,
      createdAt: '2026-01-01',
    })
    registryApi.getManifest.mockResolvedValue({
      config: { mediaType: 'application/vnd.oci.image.config.v1+json' },
      layers: [{ mediaType: 'application/vnd.oci.image.layer.v1.tar+gzip' }],
    })

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.find('.el-tag').text()).toBe('Docker Image')
  })

  it('deletes tag after confirmation', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessageBox, ElMessage } = await import('element-plus')
    registryApi.getRepositoryTags.mockResolvedValue({ tags: ['v1.0'] })
    registryApi.getManifestInfo.mockResolvedValue({
      digest: 'sha256:abc',
      contentLength: 1024,
      createdAt: '2026-01-01',
    })
    registryApi.getManifest.mockResolvedValue({ config: {}, layers: [] })
    registryApi.deleteTag.mockResolvedValue({ ok: true })
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const wrapper = createWrapper()
    await flushPromises()

    const deleteBtn = wrapper
      .findAll('.tag-card .el-button')
      .find(btn => btn.text().includes('Delete'))
    expect(deleteBtn).toBeDefined()
    await deleteBtn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(registryApi.deleteTag).toHaveBeenCalledWith('repo/one', 'v1.0')
    expect(ElMessage.success).toHaveBeenCalledWith('Tag "v1.0" deleted successfully')
  })

  it('navigates to tag detail', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositoryTags.mockResolvedValue({ tags: ['v1.0'] })
    registryApi.getManifestInfo.mockResolvedValue({
      digest: 'sha256:abc',
      contentLength: 1024,
      createdAt: '2026-01-01',
    })
    registryApi.getManifest.mockResolvedValue({ config: {}, layers: [] })

    const wrapper = createWrapper()
    await flushPromises()

    const detailBtn = wrapper
      .findAll('.tag-card .el-button')
      .find(btn => btn.text().includes('Details'))
    expect(detailBtn).toBeDefined()
    await detailBtn.trigger('click')

    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'TagDetail',
      params: { name: 'repo/one', tag: 'v1.0' },
    })
  })

  it('copies pull command to clipboard', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositoryTags.mockResolvedValue({ tags: ['latest'] })
    registryApi.getManifestInfo.mockResolvedValue({
      digest: 'sha256:abc',
      contentLength: 1024,
      createdAt: '2026-01-01',
    })
    registryApi.getManifest.mockResolvedValue({
      config: { mediaType: 'application/vnd.oci.image.config.v1+json' },
      layers: [],
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    await flushPromises()

    const copyBtn = wrapper.find('.command-input .el-button')
    await copyBtn.trigger('click')
    await flushPromises()

    expect(navigator.clipboard.writeText).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('Copied to clipboard')
  })

  it('handles fetch error gracefully', async () => {
    const { registryApi } = await import('../api/registry')
    const { ElMessage } = await import('element-plus')
    registryApi.getRepositoryTags.mockRejectedValue(new Error('network'))

    const wrapper = createWrapper()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Failed to fetch repository tags')
    expect(wrapper.vm.loading).toBe(false)
  })
})
