import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Home from './Home.vue'

const mockRouter = {
  push: vi.fn(),
}

const mockRepositories = [
  { name: 'repo/one', tagCount: 3, createdAt: '2026-01-01T00:00:00Z' },
  { name: 'repo/two', tagCount: 5, createdAt: '2026-01-02T00:00:00Z' },
]

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
}))

vi.mock('../api/registry', () => ({
  registryApi: {
    getRepositories: vi.fn(),
    deleteRepository: vi.fn(),
  },
}))

vi.mock('../composables/useAuthCheck', () => ({
  useProtectedPage: (router, fetchData) => ({
    initPage: async () => {
      if (fetchData) await fetchData()
    },
  }),
}))

vi.mock('../utils/common', () => ({
  formatDate: date => date || 'Unknown',
}))

const ElMessage = {
  success: vi.fn(),
  error: vi.fn(),
}

const ElMessageBox = {
  confirm: vi.fn(),
}

beforeEach(() => {
  vi.clearAllMocks()
  globalThis.ElMessage = ElMessage
  globalThis.ElMessageBox = ElMessageBox
})

const createWrapper = async () => {
  const wrapper = mount(Home, {
    global: {
      stubs: {
        CommonPageLayout: {
          props: ['loading', 'empty', 'items'],
          template: `
            <div class="common-layout">
              <div class="page-header">
                <slot name="actions" />
              </div>
              <div class="page-content">
                <div v-if="loading" class="loading">Loading</div>
                <div v-else-if="empty || (items && items.length === 0)" class="empty">Empty</div>
                <div v-else class="content">
                  <slot />
                </div>
              </div>
            </div>
          `,
        },
        'el-button': {
          emits: ['click'],
          template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
        },
        'el-icon': {
          template: '<span class="el-icon"><slot /></span>',
        },
        'el-table': {
          template: '<table class="el-table"><slot /></table>',
        },
        'el-table-column': {
          template: '<col class="el-table-column">',
        },
        'el-link': {
          emits: ['click'],
          template: '<a class="el-link" @click="$emit(\'click\')"><slot /></a>',
        },
        'el-tag': {
          template: '<span class="el-tag"><slot /></span>',
        },
        'el-card': {
          template: '<div class="el-card"><slot /></div>',
        },
      },
      directives: {
        loading: {
          mounted() {},
          updated() {},
        },
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('Home', () => {
  it('fetches and renders repositories on mount', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositories.mockResolvedValue(mockRepositories)

    const wrapper = await createWrapper()

    expect(registryApi.getRepositories).toHaveBeenCalled()
    expect(wrapper.vm.repositories).toEqual(mockRepositories)
  })

  it('refreshes repositories when refresh button clicked', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositories.mockResolvedValue(mockRepositories)

    const wrapper = await createWrapper()
    expect(registryApi.getRepositories).toHaveBeenCalledTimes(1)

    await wrapper.find('.page-header .el-button').trigger('click')
    await flushPromises()

    expect(registryApi.getRepositories).toHaveBeenCalledTimes(2)
  })

  it('navigates to repository detail on link click', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositories.mockResolvedValue(mockRepositories)

    const wrapper = await createWrapper()
    await flushPromises()

    const link = wrapper.find('.el-link')
    expect(link.exists()).toBe(true)
    await link.trigger('click')

    expect(mockRouter.push).toHaveBeenCalledWith('/repository/repo%2Fone')
  })

  it('deletes repository after confirmation', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositories.mockResolvedValue(mockRepositories)
    registryApi.deleteRepository.mockResolvedValue({ ok: true })
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const wrapper = await createWrapper()
    await flushPromises()

    const deleteButton = wrapper.findAll('.el-button').find(btn => btn.text().includes('Delete'))
    expect(deleteButton).toBeDefined()

    await deleteButton.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(registryApi.deleteRepository).toHaveBeenCalledWith('repo/one')
  })

  it('handles fetch error gracefully', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.getRepositories.mockRejectedValue(new Error('network error'))

    const wrapper = await createWrapper()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('Failed to fetch repositories')
    expect(wrapper.vm.loading).toBe(false)
  })
})
