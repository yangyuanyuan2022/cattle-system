const configuredBase = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
export const API_BASE_URL = configuredBase || 'http://127.0.0.1:8080/api/v1'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  timestamp: string
}

export async function request<T>(options: UniApp.RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('access_token')
    uni.request({
      ...options,
      url: `${API_BASE_URL}${options.url}`,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.header,
      },
      success(response) {
        const body = response.data as ApiResponse<T>
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(body.data)
          return
        }
        if (response.statusCode === 401) {
          uni.removeStorageSync('access_token')
          uni.reLaunch({ url: '/pages/login/index' })
        }
        reject(new Error(body?.message || `HTTP ${response.statusCode}`))
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络连接失败'))
      },
    })
  })
}
