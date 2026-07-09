import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import CommonPageLayout from './CommonPageLayout.vue'

vi.mock('./AboutDialog.vue', () => ({
  default: {
    name: 'AboutDialog',
    template: '<div class="mock-dialog"></div>',
    methods: {
      open: vi.fn(),
    },
  },
}))

const createWrapper = (props = {}, slots = {}) => {
  return mount(CommonPageLayout, {
    props: {
      title: 'Test Page',
      ...props,
    },
    slots: {
      default: '<div class="default-content">Main Content</div>',
      ...slots,
    },
    global: {
      stubs: {
        'el-card': {
          template: `
            <div class="el-card">
              <div class="el-card__header">
                <slot name="header" />
              </div>
              <div class="el-card__body">
                <slot />
              </div>
            </div>
          `,
        },
        'el-loading': {
          template: '<div class="el-loading">Loading...</div>',
        },
        'el-empty': {
          template: '<div class="el-empty"><slot /></div>',
        },
        'el-button': {
          template: '<button class="el-button"><slot /></button>',
        },
        'el-icon': {
          template: '<span class="el-icon"><slot /></span>',
        },
      },
    },
  })
}

describe('CommonPageLayout', () => {
  it('renders title in header', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.page-header h1').text()).toBe('Test Page')
  })

  it('renders default slot content', () => {
    const wrapper = createWrapper({ items: [{}] })
    expect(wrapper.find('.default-content').exists()).toBe(true)
  })

  it('renders card title when provided', () => {
    const wrapper = createWrapper({ cardTitle: 'Card Title' })
    const title = wrapper.find('.card-title')
    expect(title.exists()).toBe(true)
    expect(title.text()).toBe('Card Title')
  })

  it('shows loading state', () => {
    const wrapper = createWrapper({ loading: true })
    expect(wrapper.find('.loading-container').exists()).toBe(true)
    expect(wrapper.find('.el-loading').exists()).toBe(true)
  })

  it('shows empty state when no items', () => {
    const wrapper = createWrapper({ empty: true })
    expect(wrapper.find('.el-empty').exists()).toBe(true)
  })

  it('renders header actions slot', () => {
    const wrapper = createWrapper(
      {},
      {
        actions: '<button class="action-btn">Action</button>',
      }
    )
    expect(wrapper.find('.action-btn').exists()).toBe(true)
  })

  it('renders footer when showFooter is true', () => {
    const wrapper = createWrapper(
      { showFooter: true },
      {
        footer: '<div class="footer-content">Footer</div>',
      }
    )
    expect(wrapper.find('.footer-content').exists()).toBe(true)
  })

  it('does not render header when showHeader is false', () => {
    const wrapper = createWrapper({ showHeader: false })
    expect(wrapper.find('.page-header').exists()).toBe(false)
  })
})
