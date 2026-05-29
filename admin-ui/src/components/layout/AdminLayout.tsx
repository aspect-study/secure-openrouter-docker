import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { applyTheme, isDarkMode } from '@/lib/theme'
import {
  LayoutDashboard, MessageSquare, Cpu, Users,
  LogOut, Zap, Moon, Sun, ExternalLink, Menu
} from 'lucide-react'
import { useState } from 'react'
import { useIsMobile } from '@/hooks/useWindowSize'

const navItems = [
  { to: '/admin/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/admin/chat-logs', label: 'Chat Logs', icon: MessageSquare },
  { to: '/admin/models', label: 'Models', icon: Cpu },
  { to: '/admin/users', label: 'Users', icon: Users },
]

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [dark, setDark] = useState(isDarkMode)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const isMobile = useIsMobile()

  const toggleTheme = () => {
    const next = !dark
    setDark(next)
    applyTheme(next)
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const Sidebar = () => (
    <aside className="w-64 flex flex-col h-full bg-background border-r border-border">
      {/* Logo */}
      <div className="p-6 flex items-center gap-3 border-b border-border">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-primary text-primary-foreground">
          <Zap className="w-4 h-4" />
        </div>
        <div>
          <p className="font-semibold text-sm">OpenRouter</p>
          <p className="text-xs text-muted-foreground">Admin Panel</p>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} onClick={() => setSidebarOpen(false)}>
            {({ isActive }) => (
              <div className={cn(
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:bg-accent hover:text-foreground'
              )}>
                <Icon className="w-4 h-4" />
                {label}
              </div>
            )}
          </NavLink>
        ))}

        <div className="pt-2 border-t border-border mt-2">
          <NavLink to="/playground" onClick={() => setSidebarOpen(false)}>
            <div className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground transition-colors">
              <ExternalLink className="w-4 h-4" />
              AI Playground
            </div>
          </NavLink>
        </div>
      </nav>

      {/* User + actions */}
      <div className="p-4 border-t border-border space-y-2">
        <div className="px-3 py-2">
          <p className="text-xs font-medium truncate">{user?.email}</p>
          <p className="text-xs text-muted-foreground">Administrator</p>
        </div>
        <div className="flex gap-2">
          <Button variant="ghost" size="icon" onClick={toggleTheme} className="flex-1">
            {dark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </Button>
          <Button variant="ghost" size="icon" onClick={handleLogout} className="flex-1">
            <LogOut className="w-4 h-4" />
          </Button>
        </div>
      </div>
    </aside>
  )

  return (
    <div className="flex h-screen bg-background">
      {/* Desktop sidebar */}
      {!isMobile && <Sidebar />}

      {/* Mobile sidebar overlay */}
      {isMobile && sidebarOpen && (
        <div className="fixed inset-0 z-50">
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-black/50"
            onClick={() => setSidebarOpen(false)}
          />
          {/* Drawer */}
          <div className="absolute left-0 top-0 h-full">
            <Sidebar />
          </div>
        </div>
      )}

      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Mobile header */}
        {isMobile && (
          <div className="flex items-center gap-3 px-4 py-3 border-b border-border">
            <Button variant="ghost" size="icon" onClick={() => setSidebarOpen(true)}>
              <Menu className="w-5 h-5" />
            </Button>
            <div className="flex items-center gap-2">
              <Zap className="w-4 h-4 text-primary" />
              <span className="font-semibold text-sm">OpenRouter Admin</span>
            </div>
            <div className="ml-auto flex gap-1">
              <Button variant="ghost" size="icon" onClick={toggleTheme}>
                {dark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
              </Button>
            </div>
          </div>
        )}

        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
