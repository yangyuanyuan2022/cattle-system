import axios from 'axios'
import router from '../router'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  timestamp: string
}

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('access_token')
      localStorage.removeItem('current_user')
      if (router.currentRoute.value.path !== '/login') {
        await router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    return Promise.reject(error)
  },
)
