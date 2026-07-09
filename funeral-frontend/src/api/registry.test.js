import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { registryApi } from './registry'

vi.mock('../stores/auth', () => ({
  useAuthStore: vi.fn(() => ({
    getAuthHeader: vi.fn(() => 'Bearer test-token'),
    logout: vi.fn(),
  })),
}))

describe('registryApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('getRepositories returns data on success', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ name: 'repo1' }],
    })

    const result = await registryApi.getRepositories()
    expect(result).toEqual([{ name: 'repo1' }])
    expect(global.fetch).toHaveBeenCalledWith('/v2/repositories', {
      headers: expect.objectContaining({
        'Content-Type': 'application/json',
        Authorization: 'Bearer test-token',
      }),
    })
  })

  it('getRepositories logs out on 401', async () => {
    const { useAuthStore } = await import('../stores/auth')
    const logoutMock = vi.fn()
    useAuthStore.mockReturnValue({
      getAuthHeader: () => 'Bearer test-token',
      logout: logoutMock,
    })

    global.fetch.mockResolvedValue({
      ok: false,
      status: 401,
    })

    await expect(registryApi.getRepositories()).rejects.toThrow('Authentication required')
    expect(logoutMock).toHaveBeenCalled()
  })

  it('getRepositoryTags returns tags', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ name: 'repo', tags: ['v1', 'v2'] }),
    })

    const result = await registryApi.getRepositoryTags('my/repo')
    expect(result.tags).toEqual(['v1', 'v2'])
  })

  it('getManifestInfo returns data', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ digest: 'sha256:abc' }),
    })

    const result = await registryApi.getManifestInfo('my/repo', 'v1')
    expect(result.digest).toBe('sha256:abc')
  })

  it('deleteRepository succeeds on 200', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
    })

    const result = await registryApi.deleteRepository('my/repo')
    expect(global.fetch).toHaveBeenCalledWith('/v2/my/repo/', {
      method: 'DELETE',
      headers: expect.any(Object),
    })
    expect(result.ok).toBe(true)
  })

  it('checkBlob returns true when blob exists', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
    })

    const result = await registryApi.checkBlob('my/repo', 'sha256:abc')
    expect(result).toBe(true)
  })

  it('checkBlob returns false on error', async () => {
    global.fetch.mockRejectedValue(new Error('network'))

    const result = await registryApi.checkBlob('my/repo', 'sha256:abc')
    expect(result).toBe(false)
  })

  it('deleteTag throws on non-ok status', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 403,
    })

    await expect(registryApi.deleteTag('my/repo', 'v1')).rejects.toThrow('HTTP error! status: 403')
  })

  it('login sends credentials and returns data', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ access_token: 'token123' }),
    })

    const result = await registryApi.login('user', 'pass')
    expect(result.access_token).toBe('token123')
    expect(global.fetch).toHaveBeenCalledWith(
      '/v2/token?service=funeral-registry&scope=repository:*:pull,push',
      expect.objectContaining({
        method: 'POST',
        headers: { Authorization: 'Basic dXNlcjpwYXNz' },
      })
    )
  })

  it('login throws on invalid credentials', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 401,
    })

    await expect(registryApi.login('user', 'wrong')).rejects.toThrow('Invalid credentials')
  })

  it('loginAnonymous calls token endpoint without auth', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ access_token: 'anon-token' }),
    })

    const result = await registryApi.loginAnonymous()
    expect(result.access_token).toBe('anon-token')
    expect(global.fetch).toHaveBeenCalledWith(
      '/v2/token?service=funeral-registry&scope=repository:*:pull',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      })
    )
  })

  it('getUsers returns user list', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [{ username: 'admin' }],
    })

    const result = await registryApi.getUsers()
    expect(result).toEqual([{ username: 'admin' }])
  })

  it('createUser sends user data', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ username: 'newuser' }),
    })

    const result = await registryApi.createUser({ username: 'newuser', password: 'pass' })
    expect(result.username).toBe('newuser')
    expect(global.fetch).toHaveBeenCalledWith(
      '/funeral_addition/admin/users',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ username: 'newuser', password: 'pass' }),
      })
    )
  })

  it('getBlobContent returns text for JSON content', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      headers: { get: vi.fn(() => 'application/json') },
      text: async () => '{"foo":"bar"}',
    })

    const result = await registryApi.getBlobContent('my/repo', 'sha256:abc')
    expect(result.type).toBe('text')
    expect(result.content).toBe('{"foo":"bar"}')
  })

  it('getBlobContent returns blob for binary content', async () => {
    const blob = new Blob(['binary-data'])
    global.fetch.mockResolvedValue({
      ok: true,
      headers: { get: vi.fn(() => 'application/octet-stream') },
      blob: async () => blob,
    })

    const result = await registryApi.getBlobContent('my/repo', 'sha256:abc')
    expect(result.type).toBe('blob')
  })
})
