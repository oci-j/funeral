import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/repository/:name',
    name: 'Repository',
    component: () => import('../views/Repository.vue'),
    props: true
  },
  {
    path: '/upload',
    name: 'Upload',
    component: () => import('../views/Upload.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
