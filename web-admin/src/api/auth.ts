import { http, type ApiResponse } from './http'

export interface CurrentUser {
  userId: string
  farmId: string
  username: string
  realName: string
  roles: string[]
  permissions: string[]
}

export interface LoginResult {
  accessToken: string
  expiresIn: number
  userId: string
  farmId: string
  realName: string
  roles: string[]
}

export async function login(username: string, password: string) {
  const response = await http.post<ApiResponse<LoginResult>>('/auth/login', { username, password })
  return response.data.data
}

export async function getCurrentUser() {
  const response = await http.get<ApiResponse<CurrentUser>>('/auth/me')
  return response.data.data
}

export async function logout() {
  await http.post('/auth/logout')
}
