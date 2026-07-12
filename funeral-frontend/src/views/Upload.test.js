import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import Upload from './Upload.vue'
import { elementStubs, loadingDirective } from '../test-utils/element-stubs'

const mockAuthStore = {
  getAuthHeader: vi.fn(() => 'Bearer token'),
  logout: vi.fn(),
}

vi.mock('../stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('fetch', vi.fn())
  vi.stubGlobal('navigator', {
    clipboard: { writeText: vi.fn() },
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const createWrapper = () =>
  mount(Upload, {
    global: {
      stubs: elementStubs,
      directives: { loading: loadingDirective },
    },
  })

const batchResponse = {
  totalFiles: 1,
  successfulUploads: 1,
  failedUploads: 0,
  repositories: ['my-app'],
  manifests: [{ repository: 'my-app', tag: 'latest', layerDigests: [] }],
  blobs: [],
  results: [
    {
      fileIndex: 1,
      success: true,
      uploadResponse: { repositories: ['my-app'], manifests: [], blobs: [] },
    },
  ],
}

describe('Upload', () => {
  it('renders upload area and docker commands', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.tar-uploader').exists()).toBe(true)
    expect(wrapper.findAll('.command-input').length).toBe(3)
    expect(wrapper.find('.el-upload__tip').text()).toContain('docker save')
  })

  it('rejects unsupported file types', async () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.handleBeforeUpload({ name: 'image.png' })
    expect(result).toBe(false)
  })

  it('uploads tar files and shows result', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => batchResponse,
    })
    const wrapper = createWrapper()

    const file = new File(['content'], 'image.tar', { type: 'application/x-tar' })
    wrapper.vm.fileList = [{ name: 'image.tar', raw: file }]
    await flushPromises()

    await wrapper.find('.upload-actions .el-button').trigger('click')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith(
      '/funeral_addition/write/upload/dockertar/batch',
      expect.objectContaining({ method: 'POST' })
    )
    expect(wrapper.find('.upload-result').exists()).toBe(true)
  })

  it('handles upload error', async () => {
    global.fetch.mockRejectedValue(new Error('network error'))
    const wrapper = createWrapper()

    const file = new File(['content'], 'image.tar', { type: 'application/x-tar' })
    wrapper.vm.fileList = [{ name: 'image.tar', raw: file }]
    await flushPromises()

    await wrapper.find('.upload-actions .el-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.upload-result').exists()).toBe(true)
    expect(wrapper.find('.upload-result').text()).toContain('Upload failed')
  })

  it('warns when uploading without files', async () => {
    const { ElMessage } = await import('element-plus')
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.uploadTarFiles()
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('Please select at least one file')
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('clears result when a file is removed', async () => {
    const wrapper = createWrapper()
    wrapper.vm.uploadResult = { success: true }

    wrapper.vm.handleFileRemove()

    expect(wrapper.vm.uploadResult).toBeNull()
  })

  it('handles 401 response by logging out', async () => {
    global.fetch.mockResolvedValue({
      status: 401,
      ok: false,
      statusText: 'Unauthorized',
    })
    const { ElMessage } = await import('element-plus')

    const wrapper = createWrapper()
    const file = new File(['content'], 'image.tar', { type: 'application/x-tar' })
    wrapper.vm.fileList = [{ name: 'image.tar', raw: file }]
    await flushPromises()

    await wrapper.find('.upload-actions .el-button').trigger('click')
    await flushPromises()

    expect(mockAuthStore.logout).toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalled()
    expect(wrapper.vm.uploadResult.error).toContain('Authentication required')
  })

  it('shows failed files when batch has failures', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        totalFiles: 2,
        successfulUploads: 1,
        failedUploads: 1,
        repositories: [],
        manifests: [],
        blobs: [],
        results: [
          { fileIndex: 1, success: true, uploadResponse: { repositories: [], manifests: [], blobs: [] } },
          { fileIndex: 2, success: false, error: 'bad tar' },
        ],
      }),
    })
    const wrapper = createWrapper()

    const file = new File(['content'], 'image.tar', { type: 'application/x-tar' })
    wrapper.vm.fileList = [{ name: 'image.tar', raw: file }]
    await flushPromises()

    await wrapper.find('.upload-actions .el-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.upload-result').exists()).toBe(true)
    expect(wrapper.vm.uploadResult.batchInfo.failedUploads).toBe(1)
  })

  it('handles single file response without batch info', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        repositories: ['my-app'],
        manifests: [],
        blobs: [],
      }),
    })
    const wrapper = createWrapper()

    const file = new File(['content'], 'image.tar', { type: 'application/x-tar' })
    wrapper.vm.fileList = [{ name: 'image.tar', raw: file }]
    await flushPromises()

    await wrapper.find('.upload-actions .el-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.upload-result').exists()).toBe(true)
    expect(wrapper.find('.upload-result').text()).toContain('my-app')
  })

  it('shows error when copying command fails', async () => {
    const { ElMessage } = await import('element-plus')
    navigator.clipboard.writeText.mockRejectedValue(new Error('denied'))

    const wrapper = createWrapper()
    const copyBtn = wrapper.findAll('.command-input').at(0).find('.el-button')
    await copyBtn.trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Failed to copy to clipboard')
  })

  it('accepts supported tar file types', () => {
    const wrapper = createWrapper()
    const tarFile = new File(['content'], 'image.tar', { type: 'application/x-tar' })
    const tgzFile = new File(['content'], 'image.tgz', { type: 'application/gzip' })
    const pngFile = new File(['content'], 'image.png', { type: 'image/png' })

    expect(wrapper.vm.handleBeforeUpload(tarFile)).toBe(true)
    expect(wrapper.vm.handleBeforeUpload(tgzFile)).toBe(true)
    expect(wrapper.vm.handleBeforeUpload(pngFile)).toBe(false)
  })
})
