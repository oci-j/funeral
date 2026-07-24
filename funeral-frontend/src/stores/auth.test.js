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

  it('computes isAdmin based on auth config and roles', () => {
    const store = useAuthStore()
    // When auth is disabled, treat all users as admin
    store.authEnabled = false
    expect(store.isAdmin).toBe(true)

    store.authEnabled = true
    store.user = { username: 'admin', roles: ['ADMIN'] }
    expect(store.isAdmin).toBe(true)

    store.user = { username: 'user', roles: ['USER'] }
    expect(store.isAdmin).toBe(false)

    store.user = null
    expect(store.isAdmin).toBe(false)

    // Not checked yet: do not grant admin
    store.authEnabled = null
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

  it('checkAuthConfig throws on non-ok response and defaults to enabled', async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 500 })

    const store = useAuthStore()
    await store.checkAuthConfig()

    expect(store.authEnabled).toBe(true)
    expect(store.checkingConfig).toBe(false)
  })

  it('checkAuthConfig skips fetch while another check is in progress', async () => {
    let resolveFetch
    global.fetch.mockReturnValue(
      new Promise(resolve => {
        resolveFetch = resolve
      })
    )

    const store = useAuthStore()
    const first = store.checkAuthConfig()
    expect(store.checkingConfig).toBe(true)

    // Second call while checking should return immediately
    await store.checkAuthConfig()
    expect(global.fetch).toHaveBeenCalledTimes(1)

    resolveFetch({ ok: true, json: async () => ({ enabled: false }) })
    await first
    expect(store.authEnabled).toBe(false)
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

  it('login falls back to data.token and non-array groups', async () => {
    const { registryApi } = await import('../api/registry')
    const payload = { sub: 'user1', groups: 'not-an-array', exp: Date.now() / 1000 + 1000 }
    const token = `header.${btoa(JSON.stringify(payload))}.signature`
    registryApi.login.mockResolvedValue({ token })

    const store = useAuthStore()
    const result = await store.login('user1', 'password')

    expect(result.success).toBe(true)
    expect(store.token).toBe(token)
    expect(store.user.username).toBe('user1')
    expect(store.user.roles).toEqual([])
  })

  it('loginAnonymous stores anonymous user info', async () => {
    const { registryApi } = await import('../api/registry')
    const token = createValidToken(Date.now() / 1000 + 1000)
    registryApi.loginAnonymous.mockResolvedValue({ access_token: token })

    const store = useAuthStore()
    const result = await store.loginAnonymous()

    expect(result.success).toBe(true)
    expect(store.token).toBe(token)
    expect(store.user).toEqual({ username: 'anonymous', roles: [] })
    expect(mockStorage.getItem('token')).toBe(token)
  })

  it('loginAnonymous returns error on failure', async () => {
    const { registryApi } = await import('../api/registry')
    registryApi.loginAnonymous.mockRejectedValue(new Error('Server unavailable'))

    const store = useAuthStore()
    const result = await store.loginAnonymous()

    expect(result.success).toBe(false)
    expect(result.error).toBe('Server unavailable')
    expect(store.token).toBe('')
  })

  it('expires token on initialization', () => {
    const expiredToken = createValidToken(Math.floor(Date.now() / 1000) - 1000)
    mockStorage.setItem('token', expiredToken)

    const store = useAuthStore()
    expect(store.token).toBe('')
  })

  it('logs out on malformed token during initialization', () => {
    mockStorage.setItem('token', 'not-a-jwt-token')

    const store = useAuthStore()
    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(mockStorage.removeItem).toHaveBeenCalledWith('token')
  })
})
