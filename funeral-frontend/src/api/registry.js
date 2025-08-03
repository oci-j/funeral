import axios from 'axios'

const api = axios.create({
  baseURL: '/v2',
  timeout: 30000
})
