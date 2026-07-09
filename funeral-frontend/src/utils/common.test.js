import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { formatDate, formatFileSize, copyToClipboard, encodeRepoName, decodeRepoName, debounce } from './common'

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

import { ElMessage } from 'element-plus'

describe('formatDate', () => {
  it('returns Unknown for empty input', () => {
    expect(formatDate(null)).toBe('Unknown')
    expect(formatDate(undefined)).toBe('Unknown')
    expect(formatDate('')).toBe('Unknown')
  })

  it('formats valid date strings', () => {
    const result = formatDate('2026-01-01T00:00:00.000Z')
    expect(result).not.toBe('Unknown')
    expect(new Date(result).getTime()).toBeGreaterThan(0)
  })

  it('returns Invalid Date string for invalid date', () => {
    expect(formatDate('not a date')).toBe('Invalid Date')
  })
})

describe('formatFileSize', () => {
  it('returns 0 B for zero', () => {
    expect(formatFileSize(0)).toBe('0 B')
  })

  it('formats bytes', () => {
    expect(formatFileSize(512)).toBe('512.00 B')
  })

  it('formats kilobytes', () => {
    expect(formatFileSize(1024)).toBe('1.00 KB')
  })

  it('formats megabytes', () => {
    expect(formatFileSize(1024 * 1024)).toBe('1.00 MB')
  })

  it('formats gigabytes', () => {
    expect(formatFileSize(1024 * 1024 * 1024)).toBe('1.00 GB')
  })
})

describe('copyToClipboard', () => {
  let writeTextMock

  beforeEach(() => {
    writeTextMock = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(global.navigator, 'clipboard', {
      value: { writeText: writeTextMock },
      writable: true,
      configurable: true,
    })
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('copies text and shows success message', async () => {
    await copyToClipboard('hello')
    expect(writeTextMock).toHaveBeenCalledWith('hello')
    expect(ElMessage.success).toHaveBeenCalledWith('Copied to clipboard')
  })

  it('shows error message on failure', async () => {
    writeTextMock.mockRejectedValue(new Error('failed'))
    await copyToClipboard('hello')
    expect(ElMessage.error).toHaveBeenCalledWith('Failed to copy to clipboard')
  })
})

describe('encodeRepoName / decodeRepoName', () => {
  it('encodes and decodes repository names', () => {
    const name = 'ai-mss-analyst/gop/gop-frontend'
    const encoded = encodeRepoName(name)
    expect(encoded).toBe(encodeURIComponent(name))
    expect(decodeRepoName(encoded)).toBe(name)
  })

  it('handles names with special characters', () => {
    const name = 'my/repo@latest'
    expect(decodeRepoName(encodeRepoName(name))).toBe(name)
  })
})

describe('debounce', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('delays function execution', () => {
    const fn = vi.fn()
    const debounced = debounce(fn, 500)

    debounced('a')
    expect(fn).not.toHaveBeenCalled()

    vi.advanceTimersByTime(500)
    expect(fn).toHaveBeenCalledOnce()
    expect(fn).toHaveBeenCalledWith('a')
  })

  it('executes immediately when immediate is true', () => {
    const fn = vi.fn()
    const debounced = debounce(fn, 500, true)

    debounced('a')
    expect(fn).toHaveBeenCalledOnce()
    expect(fn).toHaveBeenCalledWith('a')
  })

  it('resets timeout on rapid calls', () => {
    const fn = vi.fn()
    const debounced = debounce(fn, 500)

    debounced('a')
    vi.advanceTimersByTime(300)
    debounced('b')
    vi.advanceTimersByTime(300)
    expect(fn).not.toHaveBeenCalled()

    vi.advanceTimersByTime(200)
    expect(fn).toHaveBeenCalledOnce()
    expect(fn).toHaveBeenCalledWith('b')
  })
})
