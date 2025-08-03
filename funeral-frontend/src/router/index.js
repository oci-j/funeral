import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Repository from '../views/Repository.vue'
import Upload from '../views/Upload.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/repository/:name',
    name: 'Repository',
    component: Repository,
    props: true
  },
  {
    path: '/upload',
    name: 'Upload',
    component: Upload
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
