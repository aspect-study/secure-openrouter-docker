import { Routes, Route, Navigate } from 'react-router-dom'
import { useEffect } from 'react'
import { Toaster } from 'sonner'
import { loadSavedTheme } from '@/lib/theme'
import { useAuth } from '@/hooks/useAuth'
import LoginPage from '@/pages/LoginPage'
import PlaygroundPage from '@/pages/PlaygroundPage'
import SettingsPage from '@/pages/SettingsPage'
import DashboardPage from '@/pages/admin/DashboardPage'
import ChatLogsPage from '@/pages/admin/ChatLogsPage'
import ModelManagerPage from '@/pages/admin/ModelManagerPage'
import UserManagerPage from '@/pages/admin/UserManagerPage'
import AgentPage from '@/pages/admin/AgentPage'
import AdminLayout from '@/components/layout/AdminLayout'

function ProtectedRoute({ children, requireAdmin = false }: {
  children: React.ReactNode
  requireAdmin?: boolean
}) {
  const { user, isAdmin } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (requireAdmin && !isAdmin) return <Navigate to="/playground" replace />
  return <>{children}</>
}

export default function App() {
  // Apply saved theme on mount
  useEffect(() => {
    loadSavedTheme()
  }, [])

  return (
    <>
      <Toaster richColors position="top-center" />
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route path="/playground" element={
          <ProtectedRoute>
            <PlaygroundPage />
          </ProtectedRoute>
        } />

        <Route path="/settings" element={
          <ProtectedRoute>
            <SettingsPage />
          </ProtectedRoute>
        } />

        <Route path="/admin" element={
          <ProtectedRoute requireAdmin>
            <AdminLayout />
          </ProtectedRoute>
        }>
          <Route index element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="chat-logs" element={<ChatLogsPage />} />
          <Route path="models" element={<ModelManagerPage />} />
          <Route path="users" element={<UserManagerPage />} />
          <Route path="agent" element={<AgentPage />} />
        </Route>

        <Route path="/" element={<Navigate to="/playground" replace />} />
        <Route path="*" element={<Navigate to="/playground" replace />} />
      </Routes>
    </>
  )
}
