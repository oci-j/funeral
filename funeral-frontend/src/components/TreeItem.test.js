import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import TreeItem from './TreeItem.vue'
import { elementStubs } from '../test-utils/element-stubs'

const fileNode = {
  name: 'readme.txt',
  path: 'readme.txt',
  type: 'file',
  size: 100,
}

const dirNode = {
  name: 'src',
  path: 'src',
  type: 'directory',
  children: [fileNode],
}

const createWrapper = (node, overrides = {}) => {
  const allExpanded = ref(true)
  const showEmptyFolders = ref(true)
  const previewFile = vi.fn()

  const wrapper = mount(TreeItem, {
    props: { node, level: 0 },
    global: {
      stubs: elementStubs,
      provide: {
        formatSize: size => `${size} B`,
        getFileType: file => (file.type === 'directory' ? 'Directory' : 'Text'),
        allExpanded,
        showEmptyFolders,
        previewFile,
        ...overrides,
      },
    },
  })

  return { wrapper, allExpanded, showEmptyFolders, previewFile }
}

const isChildVisible = wrapper => {
  const child = wrapper.find('.tree-children .tree-item')
  return child.exists() && child.element.style.display !== 'none'
}

describe('TreeItem', () => {
  it('renders a file node', () => {
    const { wrapper } = createWrapper(fileNode)
    expect(wrapper.find('.tree-row').exists()).toBe(true)
    expect(wrapper.find('.item-name').text()).toBe('readme.txt')
    expect(wrapper.find('.item-size').text()).toBe('100 B')
    expect(wrapper.find('.item-type').text()).toBe('Text')
  })

  it('renders a directory node', () => {
    const { wrapper } = createWrapper(dirNode)
    expect(wrapper.find('.item-name').text()).toBe('src')
    expect(wrapper.find('.tree-children').exists()).toBe(true)
  })

  it('toggles directory expansion on click', async () => {
    const { wrapper } = createWrapper(dirNode)
    expect(isChildVisible(wrapper)).toBe(true)

    await wrapper.find('.tree-row').trigger('click')
    await flushPromises()
    expect(isChildVisible(wrapper)).toBe(false)

    await wrapper.find('.tree-row').trigger('click')
    await flushPromises()
    expect(isChildVisible(wrapper)).toBe(true)
  })

  it('calls previewFile when a file node is clicked', async () => {
    const { wrapper, previewFile } = createWrapper(fileNode)
    await wrapper.find('.tree-row').trigger('click')
    await flushPromises()
    expect(previewFile).toHaveBeenCalledWith(fileNode)
  })

  it('hides empty folders when showEmptyFolders is false', async () => {
    const { wrapper, showEmptyFolders } = createWrapper({
      name: 'empty',
      path: 'empty',
      type: 'directory',
      children: [],
    })
    expect(wrapper.find('.tree-item').exists()).toBe(true)

    showEmptyFolders.value = false
    await flushPromises()
    expect(wrapper.find('.tree-item').exists()).toBe(false)
  })

  it('expands/collapses with allExpanded changes', async () => {
    const { wrapper, allExpanded } = createWrapper(dirNode)
    expect(isChildVisible(wrapper)).toBe(true)

    allExpanded.value = false
    await flushPromises()
    expect(isChildVisible(wrapper)).toBe(false)
  })
})
