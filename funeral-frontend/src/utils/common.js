/**
 * Common utility functions for date formatting
 */

/**
 * Format a date string to localized date
 * @param {string|Date} dateString - Date string or Date object
 * @returns {string} - Formatted date string
 */
export const formatDate = (dateString) => {
  if (!dateString) return 'Unknown'
  try {
    const date = new Date(dateString)
    return date.toLocaleString()
  } catch (error) {
    return dateString?.toString() || 'Unknown'
  }
}

/**
 * Format file size in bytes to human readable format
 * @param {number} bytes - File size in bytes
 * @returns {string} - Formatted size string
 */
export const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'

  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const size = (bytes / Math.pow(1024, i)).toFixed(2)

  return `${size} ${sizes[i]}`
}

/**
 * Copy text to clipboard
 * @param {string} text - Text to copy
 * @returns {Promise<void>}
 */
export const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('Copied to clipboard')
  } catch (error) {
    ElMessage.error('Failed to copy to clipboard')
  }
}

/**
 * Encode repository name for URL
 * @param {string} name - Repository name
 * @returns {string} - Encoded repository name
 */
export const encodeRepoName = (name) => {
  return encodeURIComponent(name)
}

/**
 * Decode repository name from URL
 * @param {string} encodedName - Encoded repository name
 * @returns {string} - Decoded repository name
 */
export const decodeRepoName = (encodedName) => {
  return decodeURIComponent(encodedName)
}

/**
 * Debounce function
 * @param {Function} func - Function to debounce
 * @param {number} wait - Wait time in milliseconds
 * @param {boolean} immediate - Execute before timeout
 * @returns {Function} - Debounced function
 */
export function debounce(func, wait = 500, immediate = false) {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      timeout = null
      if (!immediate) func.apply(this, args)
    }
    const callNow = immediate && !timeout
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
    if (callNow) func.apply(this, args)
  }
}
