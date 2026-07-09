import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'

vi.mock('../api/registry', () => ({
  registryApi: {
    login: vi.fn(),
    loginAnonymous: vi.fn(),
  },
}))

const createValidToken = exp => {
  const payload = { sub: 'admin', groups: ['ADMIN'], exp }
  const base64 = btoa(JSON.stringify(payload))
  return `header.${base64}.signature`
}

function createMockStorage() {
  let store = {}
  return {
    getItem: vi.fn(key => (key in store ? store[key] : null)),
    setItem: vi.fn((key, value) => {
      store[key] = value
    }),
    removeItem: vi.fn(key => {
      delete store[key]
    }),
    clear: vi.fn(() => {
      store = {}
    }),
    _store: store,
  }
}

describe('useAuthStore', () => {
  let mockStorage

  beforeEach(() => {
    mockStorage = createMockStorage()
    vi.stubGlobal('localStorage', mockStorage)
    vi.stubGlobal('fetch', vi.fn())
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('initializes with no token', () => {
    const store = useAuthStore()
    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(store.authEnabled).toBeNull()
  })

  it('computes isAuthenticated based on auth config', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)

    store.authEnabled = false
    expect(store.isAuthenticated).toBe(true)

    store.authEnabled = true
    expect(store.isAuthenticated).toBe(false)

    store.token = createValidToken(Date.now() / 1000 + 1000)
    expect(store.isAuthenticated).toBe(true)
  })

  it('computes isAdmin only when auth enabled and user has ADMIN role', () => {
    const store = useAuthStore()
    store.authEnabled = false
    expect(store.isAdmin).toBe(false)

    store.authEnabled = true
    store.user = { username: 'admin', roles: ['ADMIN'] }
    expect(store.isAdmin).toBe(true)

    store.user = { username: 'user', roles: ['USER'] }
    expect(store.isAdmin).toBe(false)
  })

  it('checkAuthConfig fetches auth config and sets enabled', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ enabled: true }),
    })

    const store = useAuthStore()
    await store.checkAuthConfig()

    expect(global.fetch).toHaveBeenCalledWith('/funeral_addition/config/auth')
    expect(store.authEnabled).toBe(true)
  })

  it('checkAuthConfig defaults to enabled on error', async () => {
    global.fetch.mockRejectedValue(new Error('network error'))

    const store = useAuthStore()
    await store.checkAuthConfig()

    expect(store.authEnabled).toBe(true)
  })

  it('checkAuthConfig only runs once', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ enabled: true }),
    })

    const store = useAuthStore()
    await store.checkAuthConfig()
    await store.checkAuthConfig()

    expect(global.fetch).toHaveBeenCalledTimes(1)
  })

  it('login stores token and user info', async () => {
    const { registryApi } = await import('../api/registry')
    const token = createValidToken(Date.now() / 1000 + 1000)
    registryApi.login.mockResolvedValue({ access_token: token })

    const store = useAuthStore()
    const result = await store.login('admin', 'password')

    expect(result.success).toBe(true)
    expect(store.token).toBe(token)
    expect(store.user.username).toBe('admin')
    expect(store.user.roles).toContain('ADMIN')
  })

  it('login returns error on failure', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.login.mockRejectedValue(new Error('Invalid credentials'))

    const store = useAuthStore()
    const result = await store.login('admin', 'wrong')

    expect(result.success).toBe(false)
    expect(result.error).toBe('Invalid credentials')
  })

  it('logout clears state and localStorage', () => {
    const store = useAuthStore()
    store.token = createValidToken(Date.now() / 1000 + 1000)
    store.user = { username: 'admin', roles: ['ADMIN'] }

    store.logout()

    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(mockStorage.removeItem).toHaveBeenCalledWith('token')
    expect(mockStorage.removeItem).toHaveBeenCalledWith('user')
  })

  it('getAuthHeader returns Bearer token when authenticated', () => {
    const store = useAuthStore()
    store.token = 'my-token'
    expect(store.getAuthHeader()).toBe('Bearer my-token')

    store.token = ''
    expect(store.getAuthHeader()).toBe('')
  })

  it('expires token on initialization', () => {
    const expiredToken = createValidToken(Math.floor(Date.now() / 1000) - 1000)
    mockStorage.setItem('token', expiredToken)

    const store = useAuthStore()
    expect(store.token).toBe('')
  })
})
