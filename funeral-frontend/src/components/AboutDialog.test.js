import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AboutDialog from './AboutDialog.vue'
import { elementStubs, iconStubs, loadingDirective } from '../test-utils/element-stubs'

const createWrapper = () =>
  mount(AboutDialog, {
    global: {
      stubs: { ...elementStubs, ...iconStubs },
      directives: { loading: loadingDirective },
    },
  })

const mockRuntime = (overrides = {}) => ({
  isNativeImage: false,
  javaVersion: '17',
  osName: 'Linux',
  osArch: 'amd64',
  pid: 123,
  canDownload: false,
  binaryName: 'funeral',
  ...overrides,
})

describe('AboutDialog', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock-url'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('opens dialog and fetches runtime info', async () => {
    const wrapper = createWrapper()
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => mockRuntime(),
    })

    await wrapper.vm.open()
    await flushPromises()

    expect(wrapper.find('.el-dialog').exists()).toBe(true)
    expect(wrapper.find('.project-name').text()).toBe('FUNERAL')
    expect(global.fetch).toHaveBeenCalledWith('/funeral_addition/config/runtime')
  })

  it('displays runtime info after loading', async () => {
    const wrapper = createWrapper()
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => mockRuntime(),
    })

    await wrapper.vm.open()
    await flushPromises()

    expect(wrapper.find('.runtime-info-row').exists()).toBe(true)
    expect(wrapper.find('.runtime-tag').text()).toContain('JVM')
  })

  it('shows download button when binary is downloadable', async () => {
    const wrapper = createWrapper()
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => mockRuntime({ canDownload: true, binarySize: 1024 }),
    })

    await wrapper.vm.open()
    await flushPromises()

    expect(wrapper.find('.download-section-inline').exists()).toBe(true)
  })

  it('downloads binary when download button is clicked', async () => {
    const wrapper = createWrapper()
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockRuntime({ canDownload: true, binarySize: 1024 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        headers: {
          get: name => (name === 'content-disposition' ? 'filename="funeral-binary"' : null),
        },
        blob: async () => new Blob(['binary']),
      })

    await wrapper.vm.open()
    await flushPromises()

    const downloadBtn = wrapper.find('.download-section-inline .el-button')
    expect(downloadBtn.exists()).toBe(true)
    await downloadBtn.trigger('click')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/funeral_addition/config/download/binary')
    expect(clickSpy).toHaveBeenCalled()
  })

  it('handles runtime fetch error gracefully', async () => {
    const wrapper = createWrapper()
    global.fetch.mockRejectedValue(new Error('network error'))

    await wrapper.vm.open()
    await flushPromises()

    expect(wrapper.find('.el-dialog').exists()).toBe(true)
    expect(wrapper.find('.runtime-info-row').exists()).toBe(false)
  })

  it('closes the dialog', async () => {
    const wrapper = createWrapper()
    global.fetch.mockResolvedValue({ ok: true, json: async () => mockRuntime() })

    await wrapper.vm.open()
    await flushPromises()
    expect(wrapper.find('.el-dialog').exists()).toBe(true)

    await wrapper.vm.close()
    expect(wrapper.find('.el-dialog').exists()).toBe(false)
  })
})
