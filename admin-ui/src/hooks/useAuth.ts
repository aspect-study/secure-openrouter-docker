import { useState, useCallback } from 'react'
import { authApi } from '@/lib/api'

export interface AuthUser {
  email: string
  role: 'USER' | 'ADMIN'
  token: string
}

function parseJwt(token: string): { sub: string; role?: string } | null {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload))
  } catch {
    return null
  }
}

function loadUserFromStorage(): AuthUser | null {
  const token = localStorage.getItem('token')
  if (!token) return null
  const payload = parseJwt(token)
  if (!payload) return null
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try { return JSON.parse(userStr) } catch { return null }
  }
  return null
}

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(loadUserFromStorage)

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login(email, password)
    const { token } = response.data

    // Decode role from JWT payload (our backend sets ROLE_ prefix in authorities)
    // Since our JWT only stores email as subject, we fetch role from a profile call
    // For simplicity, we store what we know and update on first admin route hit
    const authUser: AuthUser = {
      email,
      role: 'USER', // will be updated after profile fetch
      token,
    }

    // Try to determine role from a quick admin stats call
    try {
      localStorage.setItem('token', token)
      const { default: api } = await import('@/lib/api')
      await api.get('/admin/stats')
      authUser.role = 'ADMIN'
    } catch {
      authUser.role = 'USER'
    }

    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(authUser))
    setUser(authUser)
    return authUser
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setUser(null)
  }, [])

  return { user, login, logout, isAdmin: user?.role === 'ADMIN' }
}
