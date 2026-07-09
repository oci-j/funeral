import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TarViewer from './TarViewer.vue'
import { elementStubs, iconStubs } from '../test-utils/element-stubs'
import pako from 'pako'
import untar from 'js-untar'

vi.mock('pako', () => ({
  default: { inflate: vi.fn() },
}))

vi.mock('js-untar', () => ({
  default: vi.fn(),
}))

const FilePreviewStub = {
  name: 'FilePreviewStub',
  props: ['fileName', 'fileContent', 'fileSize', 'visible'],
  template: '<div class="file-preview-stub">{{ fileName }}</div>',
}

const createArrayBuffer = () => new Uint8Array([0x1f, 0x8b, 0x08, 0x00]).buffer

const fileEntry = (name, content = new ArrayBuffer(4)) => ({
  name,
  size: content.byteLength,
  type: '0',
  mode: 0o644,
  mtime: 0,
  uid: 0,
  gid: 0,
  linkname: null,
  buffer: content,
})

beforeEach(() => {
  vi.clearAllMocks()
  pako.inflate.mockImplementation(buf => new Uint8Array(buf))
})

afterEach(() => {})

const createWrapper = (props = {}) =>
  mount(TarViewer, {
    props: { arrayBuffer: createArrayBuffer(), mediaType: '', ...props },
    global: {
      stubs: { ...elementStubs, ...iconStubs, FilePreview: FilePreviewStub },
    },
  })

const waitForParse = async _wrapper => {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 0))
  await flushPromises()
}

describe('TarViewer', () => {
  it('renders archive stats and file tree after parsing', async () => {
    untar.mockImplementation(() => {
      const entries = [fileEntry('readme.txt'), fileEntry('data.json')]
      return {
        progress: vi.fn(() => Promise.resolve(entries)),
      }
    })

    const wrapper = createWrapper()
    await waitForParse(wrapper)

    expect(wrapper.find('.tar-header').text()).toContain('2 files')
    expect(wrapper.find('.tree-header').exists()).toBe(true)
    expect(wrapper.findAll('.tree-row').length).toBe(2)
  })

  it('toggles expand/collapse all', async () => {
    untar.mockImplementation(() => {
      const entries = [fileEntry('a/b.txt')]
      return {
        progress: vi.fn(() => Promise.resolve(entries)),
      }
    })

    const wrapper = createWrapper()
    await waitForParse(wrapper)

    expect(wrapper.vm.allExpanded).toBe(true)
    const collapseBtn = wrapper.findAll('.header-actions .el-button').find(function (btn) {
      return btn.text().includes('Collapse')
    })
    expect(collapseBtn).toBeDefined()
    await collapseBtn.trigger('click')
    expect(wrapper.vm.allExpanded).toBe(false)
  })

  it('toggles empty folder visibility', async () => {
    untar.mockImplementation(() => {
      const entries = [{ name: 'empty/', size: 0, type: '5', mode: 0o755, mtime: 0, uid: 0, gid: 0, linkname: null, buffer: null }]
      return {
        progress: vi.fn(() => Promise.resolve(entries)),
      }
    })

    const wrapper = createWrapper()
    await waitForParse(wrapper)

    expect(wrapper.vm.showEmptyFolders).toBe(true)
    const toggleBtn = wrapper.findAll('.header-actions .el-button').find(function (btn) {
      return btn.text().includes('Hide Empty')
    })
    expect(toggleBtn).toBeDefined()
    await toggleBtn.trigger('click')
    expect(wrapper.vm.showEmptyFolders).toBe(false)
  })

  it('opens file preview when a file row is clicked', async () => {
    untar.mockImplementation(() => {
      const entries = [fileEntry('readme.txt')]
      return {
        progress: vi.fn(() => Promise.resolve(entries)),
      }
    })

    const wrapper = createWrapper()
    await waitForParse(wrapper)

    await wrapper.find('.tree-row.is-file').trigger('click')
    await flushPromises()

    expect(wrapper.vm.previewVisible).toBe(true)
    expect(wrapper.find('.file-preview-stub').exists()).toBe(true)
  })

  it('parses plain tar archives when gzip decompression fails', async () => {
    pako.inflate.mockImplementation(() => {
      throw new Error('not gzip')
    })
    untar.mockImplementation(() => {
      const entries = [fileEntry('plain.txt')]
      return {
        progress: vi.fn(() => Promise.resolve(entries)),
      }
    })

    const wrapper = createWrapper()
    await waitForParse(wrapper)

    expect(wrapper.find('.tree-row').exists()).toBe(true)
  })

  it('shows error state when tar parsing fails', async () => {
    untar.mockImplementation(() => ({
      progress: vi.fn(() => Promise.reject(new Error('bad tar'))),
    }))

    const wrapper = createWrapper()
    await waitForParse(wrapper)

    expect(wrapper.find('.error-state').exists()).toBe(true)
    expect(wrapper.find('.el-alert').text()).toContain('bad tar')
  })

  it('shows error state for empty archives', async () => {
    untar.mockImplementation(() => ({
      progress: vi.fn(() => Promise.resolve([])),
    }))

    const wrapper = createWrapper()
    await waitForParse(wrapper)

    expect(wrapper.find('.error-state').exists()).toBe(true)
    expect(wrapper.find('.el-alert').text()).toContain('No files found')
  })
})
