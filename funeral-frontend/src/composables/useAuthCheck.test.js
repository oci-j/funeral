import { describe, it, expect, vi } from 'vitest'
import { useAuthCheck, useAdminCheck, useProtectedPage } from './useAuthCheck'

vi.mock('../stores/auth', () => ({
  useAuthStore: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    warning: vi.fn(),
  },
}))

describe('useAuthCheck', () => {
  it('returns true when already authenticated', async () => {
    const { useAuthStore } = await import('../stores/auth')
    useAuthStore.mockReturnValue({
      isAuthenticated: true,
      checkAuthConfig: vi.fn(),
    })

    const router = { push: vi.fn() }
    const { checkAuth } = useAuthCheck(router)
    const result = await checkAuth()

    expect(result).toBe(true)
    expect(router.push).not.toHaveBeenCalled()
  })

  it('redirects to login when auth enabled and not authenticated', async () => {
    const { useAuthStore } = await import('../stores/auth')
    const { ElMessage } = await import('element-plus')
    useAuthStore.mockReturnValue({
      isAuthenticated: false,
      authEnabled: true,
      checkAuthConfig: vi.fn(),
    })

    const router = { push: vi.fn() }
    const { checkAuth } = useAuthCheck(router)
    const result = await checkAuth()

    expect(result).toBe(false)
    expect(router.push).toHaveBeenCalledWith('/login')
    expect(ElMessage.warning).toHaveBeenCalledWith('Please login to access the registry')
  })

  it('returns true when auth is disabled', async () => {
    const { useAuthStore } = await import('../stores/auth')
    useAuthStore.mockReturnValue({
      isAuthenticated: false,
      authEnabled: false,
      checkAuthConfig: vi.fn(),
    })

    const router = { push: vi.fn() }
    const { checkAuth } = useAuthCheck(router)
    const result = await checkAuth()

    expect(result).toBe(true)
  })
})

describe('useAdminCheck', () => {
  it('returns admin status', async () => {
    const { useAuthStore } = await import('../stores/auth')
    useAuthStore.mockReturnValue({
      authEnabled: true,
      checkingConfig: false,
      checkAuthConfig: vi.fn(),
      isAdmin: true,
    })

    const { checkAdmin } = useAdminCheck()
    const result = await checkAdmin()

    expect(result).toBe(true)
  })

  it('waits for config check to finish', async () => {
    const { useAuthStore } = await import('../stores/auth')
    const storeState = {
      authEnabled: null,
      checkingConfig: true,
      checkAuthConfig: vi.fn(),
      isAdmin: true,
    }
    useAuthStore.mockReturnValue(storeState)

    const { checkAdmin } = useAdminCheck()
    const promise = checkAdmin()

    // Simulate config check finishing
    setTimeout(() => {
      storeState.checkingConfig = false
    }, 100)

    const result = await promise
    expect(result).toBe(true)
  })
})

describe('useProtectedPage', () => {
  it('fetches data when authenticated', async () => {
    const { useAuthStore } = await import('../stores/auth')
    useAuthStore.mockReturnValue({
      isAuthenticated: true,
      checkAuthConfig: vi.fn(),
    })

    const router = { push: vi.fn() }
    const fetchData = vi.fn()
    const { initPage } = useProtectedPage(router, fetchData)

    await initPage()

    expect(fetchData).toHaveBeenCalled()
  })

  it('does not fetch data when not authenticated', async () => {
    const { useAuthStore } = await import('../stores/auth')
    useAuthStore.mockReturnValue({
      isAuthenticated: false,
      authEnabled: true,
      checkAuthConfig: vi.fn(),
    })

    const router = { push: vi.fn() }
    const fetchData = vi.fn()
    const loading = { value: true }
    const { initPage } = useProtectedPage(router, fetchData, { loading })

    await initPage()

    expect(fetchData).not.toHaveBeenCalled()
    expect(loading.value).toBe(false)
  })
})
