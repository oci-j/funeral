import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/repository/:name',
    name: 'Repository',
    component: () => import('../views/Repository.vue'),
    props: true,
    meta: { requiresAuth: true }
  },
  {
    path: '/repository/:name/tag/:tag',
    name: 'TagDetail',
    component: () => import('../views/TagDetail.vue'),
    props: true,
    meta: { requiresAuth: true }
  },
  {
    path: '/upload',
    name: 'Upload',
    component: () => import('../views/Upload.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/mirror',
    name: 'Mirror',
    component: () => import('../views/Mirror.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/Admin.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // Skip auth check for login page
  if (to.path === '/login') {
    // If auth is disabled, don't allow access to login page
    if (!authStore.authEnabled) {
      next('/')
      return
    }
    // Redirect logged-in users away from login page
    if (authStore.isAuthenticated) {
      next('/')
      return
    }
    next()
    return
  }

  // Check if route requires authentication
  // If auth is enabled and user is not authenticated, redirect to login
  if (to.meta.requiresAuth && authStore.authEnabled && !authStore.isAuthenticated) {
    next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
    return
  }

  // Check if route requires admin privileges
  if (to.meta.requiresAdmin && authStore.authEnabled && !authStore.isAdmin) {
    ElMessage.error('Access denied: Admin privileges required')
    next('/')
    return
  }

  // Allow access
  next()
})

export default router
