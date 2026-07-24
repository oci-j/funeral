import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FilePreview from './FilePreview.vue'
import { elementStubs, iconStubs } from '../test-utils/element-stubs'
import * as cryptoJS from 'crypto-js'

vi.mock('vue-json-pretty', () => ({
  default: { template: '<div class="vue-json-pretty-stub" />' },
}))
vi.mock('vue-json-pretty/lib/styles.css', () => ({}))

vi.mock('crypto-js', async () => {
  const { vi } = await import('vitest')
  const md5 = vi.fn(() => ({ toString: () => 'mock-md5' }))
  const sha256 = vi.fn(() => ({ toString: () => 'mock-sha256' }))
  const init = vi.fn(() => ({}))
  return {
    default: { MD5: md5, SHA256: sha256, lib: { WordArray: { init } } },
    MD5: md5,
    SHA256: sha256,
    lib: { WordArray: { init } },
  }
})

let createObjectURLSpy
let revokeObjectURLSpy
let anchorClickSpy

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } })
  createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')
  revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL')
  anchorClickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
  createObjectURLSpy.mockRestore()
  revokeObjectURLSpy.mockRestore()
  anchorClickSpy.mockRestore()
})

const createWrapper = (props = {}) =>
  mount(FilePreview, {
    props: { visible: false, fileName: '', fileContent: '', fileSize: 0, ...props },
    global: { stubs: { ...elementStubs, ...iconStubs } },
  })

const openDialog = async wrapper => {
  await wrapper.setProps({ visible: true })
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 50))
  await flushPromises()
}

describe('FilePreview', () => {
  it('displays text content and file hashes', async () => {
    const wrapper = createWrapper({ fileName: 'test.txt', fileContent: 'Hello world', fileSize: 11 })
    await openDialog(wrapper)

    expect(wrapper.find('.text-content').text()).toBe('Hello world')
    expect(wrapper.find('.file-metadata').exists()).toBe(true)
    expect(wrapper.find('.hash-text').text()).toContain('mock-md5')
    expect(cryptoJS.MD5).toHaveBeenCalled()
  })

  it('renders JSON content with vue-json-pretty stub', async () => {
    const wrapper = createWrapper({
      fileName: 'data.json',
      fileContent: '{"foo":"bar"}',
      fileSize: 13,
    })
    await openDialog(wrapper)

    expect(wrapper.find('.vue-json-pretty').exists()).toBe(true)
  })

  it('renders image content using object URL', async () => {
    const buffer = new ArrayBuffer(8)
    const wrapper = createWrapper({ fileName: 'image.png', fileContent: buffer, fileSize: 8 })
    await openDialog(wrapper)

    expect(wrapper.find('.preview-image').exists()).toBe(true)
    expect(createObjectURLSpy).toHaveBeenCalled()
  })

  it('displays binary warning for invalid UTF-8 content', async () => {
    const buffer = new Uint8Array([0xff, 0xfe, 0xfd]).buffer
    const wrapper = createWrapper({ fileName: 'binary.bin', fileContent: buffer, fileSize: 3 })
    await openDialog(wrapper)

    expect(wrapper.find('.binary-viewer').exists()).toBe(true)
    expect(wrapper.find('.el-alert--warning').exists()).toBe(true)
  })

  it('treats large files without text extension as binary', async () => {
    const size = 1024 * 1024 + 10
    const buffer = new Uint8Array(size).buffer
    const wrapper = createWrapper({ fileName: 'data.dat', fileContent: buffer, fileSize: size })
    await openDialog(wrapper)

    expect(wrapper.vm.contentType).toBe('binary')
    expect(wrapper.find('.binary-viewer').exists()).toBe(true)
  })

  it('treats content with mostly high bytes as binary', async () => {
    const bytes = new Uint8Array(100)
    bytes.fill(0x80, 0, 60)
    bytes.fill(0x61, 60)
    const wrapper = createWrapper({ fileName: 'data.dat', fileContent: bytes.buffer, fileSize: 100 })
    await openDialog(wrapper)

    expect(wrapper.vm.contentType).toBe('binary')
  })

  it('treats invalid UTF-8 with few high bytes and no control chars as text', async () => {
    const bytes = new Uint8Array(100)
    bytes.fill(0x61, 0, 80)
    bytes.fill(0x80, 80)
    const wrapper = createWrapper({ fileName: 'data.dat', fileContent: bytes.buffer, fileSize: 100 })
    await openDialog(wrapper)

    expect(wrapper.vm.contentType).toBe('text')
    expect(wrapper.find('.text-viewer').exists()).toBe(true)
  })

  it('handles files without extension using content detection', async () => {
    const wrapper = createWrapper({ fileName: 'Dockerfile', fileContent: 'FROM alpine', fileSize: 11 })
    await openDialog(wrapper)

    expect(wrapper.vm.contentType).toBe('text')
    expect(wrapper.find('.text-content').text()).toBe('FROM alpine')
  })

  it('renders invalid JSON content as stringified null data', async () => {
    const wrapper = createWrapper({
      fileName: 'broken.json',
      fileContent: '{ not valid json',
      fileSize: 16,
    })
    await openDialog(wrapper)

    expect(wrapper.vm.contentType).toBe('json')
    expect(wrapper.vm.jsonData).toBeNull()
  })

  it('shows error when file content is missing', async () => {
    const wrapper = createWrapper({ fileName: '' })
    await openDialog(wrapper)

    expect(wrapper.find('.error-state').exists()).toBe(true)
    expect(wrapper.find('.el-alert').text()).toContain('No file content to preview')
  })

  it('closes dialog and resets state when Close is clicked', async () => {
    const wrapper = createWrapper({ fileName: 'test.txt', fileContent: 'Hello', fileSize: 5 })
    await openDialog(wrapper)
    expect(wrapper.find('.text-content').exists()).toBe(true)

    await wrapper.findAll('.el-dialog__footer .el-button').at(0).trigger('click')
    await flushPromises()

    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible').at(-1)).toEqual([false])
    expect(wrapper.vm.contentType).toBe('')
  })

  it('copies text content to clipboard', async () => {
    const wrapper = createWrapper({ fileName: 'test.txt', fileContent: 'Hello', fileSize: 5 })
    await openDialog(wrapper)

    await wrapper.findAll('.el-dialog__footer .el-button').at(1).trigger('click')
    await flushPromises()

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('Hello')
  })

  it('downloads file via anchor click', async () => {
    const wrapper = createWrapper({ fileName: 'test.txt', fileContent: 'Hello', fileSize: 5 })
    await openDialog(wrapper)

    await wrapper.findAll('.el-dialog__footer .el-button').at(2).trigger('click')
    await flushPromises()

    expect(createObjectURLSpy).toHaveBeenCalled()
    expect(anchorClickSpy).toHaveBeenCalled()
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock-url')
  })

  it('closes the dialog via the header close button', async () => {
    const wrapper = createWrapper({ fileName: 'test.txt', fileContent: 'Hello', fileSize: 5 })
    await openDialog(wrapper)

    await wrapper.find('.el-dialog__close').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible').at(-1)).toEqual([false])
  })
})
