import type { CurrentUser } from '../api/auth'

const TOKEN_KEY = 'access_token'
const USER_KEY = 'current_user'

export const getToken = () => localStorage.getItem(TOKEN_KEY)

export function saveSession(token: string, user: CurrentUser) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getStoredUser(): CurrentUser | null {
  const value = localStorage.getItem(USER_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as CurrentUser
  } catch {
    clearSession()
    return null
  }
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
