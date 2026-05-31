import React, { useState, useCallback } from 'react'
import { authApi } from '@/lib/api'
import { AuthContext, AuthUser } from './useAuth'

function loadUserFromStorage(): AuthUser | null {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = token.split('.')[1]
    const decoded = JSON.parse(atob(payload))
    if (!decoded) return null
  } catch {
    return null
  }
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try { return JSON.parse(userStr) as AuthUser } catch { return null }
  }
  return null
}

// AuthProvider must wrap the Router so all components share one auth instance.
// Without this, each useAuth() call creates its own useState -- login() in
// LoginPage updates its copy but ProtectedRoute in App.tsx reads a fresh null,
// immediately bouncing the user back to /login.
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadUserFromStorage)

  const login = useCallback(async (email: string, password: string): Promise<AuthUser> => {
    const response = await authApi.login(email, password)
    const token = (response.data as { token: string }).token
    const authUser: AuthUser = { email, role: 'USER', token }

    // Raw fetch bypasses the Axios 401 interceptor. A regular USER hitting
    // /admin/stats gets 401 -- without raw fetch the interceptor calls
    // window.location.href = '/login' mid-login, hard-redirecting before
    // setUser() or navigate() can run.
    try {
      const res = await fetch('/api/admin/stats', {
        headers: { Authorization: 'Bearer ' + token },
      })
      authUser.role = res.ok ? 'ADMIN' : 'USER'
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

  return (
    <AuthContext.Provider value={{ user, login, logout, isAdmin: user?.role === 'ADMIN' }}>
      {children}
    </AuthContext.Provider>
  )
}
