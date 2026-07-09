import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AboutButton from './AboutButton.vue'

const openMock = vi.fn()

vi.mock('./AboutDialog.vue', () => ({
  default: {
    name: 'AboutDialog',
    template: '<div class="mock-dialog"></div>',
    setup() {
      return { open: openMock }
    },
  },
}))

describe('AboutButton', () => {
  beforeEach(() => {
    openMock.mockClear()
  })

  it('renders button', () => {
    const wrapper = mount(AboutButton, {
      global: {
        stubs: {
          'el-button': {
            template: '<button class="el-button"><slot /></button>',
          },
          'el-icon': {
            template: '<span class="el-icon"><slot /></span>',
          },
        },
      },
    })

    expect(wrapper.find('.about-button-wrapper').exists()).toBe(true)
    expect(wrapper.find('.el-button').exists()).toBe(true)
  })

  it('opens about dialog when clicked', async () => {
    const wrapper = mount(AboutButton, {
      global: {
        stubs: {
          'el-button': {
            template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
          },
          'el-icon': {
            template: '<span class="el-icon"><slot /></span>',
          },
        },
      },
    })

    await wrapper.find('.el-button').trigger('click')
    expect(openMock).toHaveBeenCalled()
  })

  it('toggles showText on mouse events', async () => {
    const wrapper = mount(AboutButton, {
      global: {
        stubs: {
          'el-button': {
            template: '<button class="el-button"><slot /></button>',
          },
          'el-icon': {
            template: '<span class="el-icon"><slot /></span>',
          },
          'transition': {
            template: '<div><slot /></div>',
          },
        },
      },
    })

    expect(wrapper.vm.showText).toBe(false)

    await wrapper.find('.el-button').trigger('mouseenter')
    expect(wrapper.vm.showText).toBe(true)

    await wrapper.find('.el-button').trigger('mouseleave')
    expect(wrapper.vm.showText).toBe(false)
  })
})
