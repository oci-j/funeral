import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

/**
 * Checks authentication status and redirects to login if not authenticated
 * @param {Function} router - Vue Router instance
 * @returns {Promise<boolean>} - Returns true if authenticated, false otherwise
 */
export const useAuthCheck = (router) => {
  const authStore = useAuthStore()

  const checkAuth = async () => {
    // If already authenticated, return immediately
    if (authStore.isAuthenticated) {
      return true
    }

    // Wait for auth config to be checked
    await authStore.checkAuthConfig()

    // If still not authenticated and auth is enabled, redirect to login
    if (!authStore.isAuthenticated && authStore.authEnabled !== false) {
      ElMessage.warning('Please login to access the registry')
      router.push('/login')
      return false
    }

    return true
  }

  return { checkAuth }
}

/**
 * Checks if user has admin privileges
 * @returns {Promise<boolean>} - Returns true if user is admin or auth is disabled
 */
export const useAdminCheck = () => {
  const authStore = useAuthStore()

  const checkAdmin = async () => {
    // Wait for auth config to be loaded first
    if (authStore.authEnabled === null && !authStore.checkingConfig) {
      await authStore.checkAuthConfig()
    } else if (authStore.checkingConfig) {
      // Wait for ongoing check to complete
      while (authStore.checkingConfig) {
        await new Promise(resolve => setTimeout(resolve, 50))
      }
    }

    return authStore.isAdmin
  }

  return { checkAdmin }
}

/**
 * Common onMounted hook for protected pages
 * @param {Function} router - Vue Router instance
 * @param {Function} fetchData - Function to fetch page data
 * @param {Object} options - Additional options (loading ref, etc.)
 */
export const useProtectedPage = (router, fetchData, options = {}) => {
  const { checkAuth } = useAuthCheck(router)
  const { loading } = options

  const initPage = async () => {
    const isAuthenticated = await checkAuth()

    if (!isAuthenticated) {
      // Auth check failed, don't proceed
      if (loading) loading.value = false
      return
    }

    // Auth successful, fetch data
    if (fetchData) {
      try {
        await fetchData()
      } catch (error) {
        console.error('Failed to fetch page data:', error)
      }
    }
  }

  return { initPage }
}
