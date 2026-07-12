import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRegistryStore } from './index'

describe('useRegistryStore', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('fetchRepositories sets repositories on success', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ repositories: ['repo1', 'repo2'] }),
    })

    const store = useRegistryStore()
    await store.fetchRepositories()

    expect(store.loading).toBe(false)
    expect(store.repositories).toEqual(['repo1', 'repo2'])
    expect(global.fetch).toHaveBeenCalledWith('/v2/_catalog')
  })

  it('fetchRepositories handles empty response', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({}),
    })

    const store = useRegistryStore()
    await store.fetchRepositories()

    expect(store.repositories).toEqual([])
  })

  it('fetchRepositories does not throw on network error', async () => {
    global.fetch.mockRejectedValue(new Error('network'))

    const store = useRegistryStore()
    await expect(store.fetchRepositories()).resolves.toBeUndefined()
    expect(store.loading).toBe(false)
    expect(store.repositories).toEqual([])
  })

  it('fetchRepository returns data on success', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ name: 'repo', tags: ['v1'] }),
    })

    const store = useRegistryStore()
    const result = await store.fetchRepository('repo')

    expect(result).toEqual({ name: 'repo', tags: ['v1'] })
    expect(global.fetch).toHaveBeenCalledWith('/v2/repo/tags/list')
  })

  it('fetchRepository returns undefined on non-ok response', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 404,
    })

    const store = useRegistryStore()
    const result = await store.fetchRepository('missing')

    expect(result).toBeUndefined()
  })

  it('fetchRepository does not throw on network error', async () => {
    global.fetch.mockRejectedValue(new Error('network'))

    const store = useRegistryStore()
    await expect(store.fetchRepository('repo')).resolves.toBeUndefined()
  })

  it('deleteRepository refreshes list on success', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ repositories: [] }),
      })

    const store = useRegistryStore()
    const result = await store.deleteRepository('repo')

    expect(result).toBe(true)
    expect(global.fetch).toHaveBeenCalledWith('/v2/repo/', expect.objectContaining({ method: 'DELETE' }))
    expect(global.fetch).toHaveBeenCalledTimes(2)
  })

  it('deleteRepository returns false on non-ok response', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 403,
    })

    const store = useRegistryStore()
    const result = await store.deleteRepository('repo')

    expect(result).toBe(false)
    expect(global.fetch).toHaveBeenCalledTimes(1)
  })

  it('deleteRepository returns false on network error', async () => {
    global.fetch.mockRejectedValue(new Error('network'))

    const store = useRegistryStore()
    const result = await store.deleteRepository('repo')

    expect(result).toBe(false)
  })
})
