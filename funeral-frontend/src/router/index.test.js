import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockAuthStore = {
  authEnabled: true,
  isAuthenticated: false,
  isAdmin: false,
}

vi.mock('../stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

beforeEach(() => {
  vi.resetModules()
  vi.clearAllMocks()
  mockAuthStore.authEnabled = true
  mockAuthStore.isAuthenticated = false
  mockAuthStore.isAdmin = false
  globalThis.ElMessage = { error: vi.fn() }
  globalThis.history.replaceState({}, '', '/')
})

const importRouter = () => import('../router/index.js')

describe('router', () => {
  it('redirects to login for protected routes when not authenticated', async () => {
    const { default: router } = await importRouter()
    await router.push('/upload')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/upload')
  })

  it('allows access to protected routes when authenticated', async () => {
    mockAuthStore.isAuthenticated = true
    const { default: router } = await importRouter()
    await router.push('/upload')

    expect(router.currentRoute.value.path).toBe('/upload')
  })

  it('allows access to the home route when auth is disabled', async () => {
    mockAuthStore.authEnabled = false
    const { default: router } = await importRouter()
    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('redirects away from login when auth is disabled', async () => {
    mockAuthStore.authEnabled = false
    const { default: router } = await importRouter()
    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('redirects away from login when already authenticated', async () => {
    mockAuthStore.isAuthenticated = true
    const { default: router } = await importRouter()
    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('allows access to login when auth is enabled and not authenticated', async () => {
    const { default: router } = await importRouter()
    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('redirects from admin for non-admin users', async () => {
    mockAuthStore.isAuthenticated = true
    const { default: router } = await importRouter()
    await router.push('/admin')

    expect(globalThis.ElMessage.error).toHaveBeenCalledWith(
      'Access denied: Admin privileges required'
    )
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('allows access to admin for admin users', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    const { default: router } = await importRouter()
    await router.push('/admin')

    expect(router.currentRoute.value.path).toBe('/admin')
  })

  it('preserves redirect query for repository routes', async () => {
    const { default: router } = await importRouter()
    await router.push('/repository/test-repo')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/repository/test-repo')
  })

  it('resolves all route component factories', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    const { default: router } = await importRouter()

    await router.push('/')
    await router.push('/repository/test-repo')
    await router.push('/repository/test-repo/tag/v1.0')
    await router.push('/upload')
    await router.push('/mirror')
    await router.push('/mirror-helm')

    expect(router.currentRoute.value.path).toBe('/mirror-helm')
  })
})
