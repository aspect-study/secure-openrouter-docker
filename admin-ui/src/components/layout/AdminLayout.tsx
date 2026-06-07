import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { applyTheme, isDarkMode } from '@/lib/theme'
import {
  LayoutDashboard, MessageSquare, Cpu, Users, Bot,
  LogOut, Zap, Moon, Sun, ExternalLink, Menu, KeyRound
} from 'lucide-react'
import { useState } from 'react'
import { useIsMobile } from '@/hooks/useWindowSize'
import { ChangePasswordDialog } from '@/components/ui/change-password-dialog'

const navItems = [
  { to: '/admin/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/admin/chat-logs', label: 'Chat Logs', icon: MessageSquare },
  { to: '/admin/models', label: 'Models', icon: Cpu },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/agent', label: 'Agent', icon: Bot },
]

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [dark, setDark] = useState(isDarkMode)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [changePwOpen, setChangePwOpen] = useState(false)
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
    <aside className="w-64 flex flex-col h-full border-r border-border" style={{background: 'var(--sidebar)'}}>
      {/* Logo */}
      <div className="px-5 py-5 flex items-center gap-3">
        <div className="relative shrink-0">
          <div className="absolute inset-0 rounded-xl blur-sm opacity-50" style={{background: 'var(--primary)'}} />
          <div className="relative flex items-center justify-center w-8 h-8 rounded-xl text-primary-foreground shadow-sm" style={{background: 'var(--primary)'}}>
            <Zap className="w-4 h-4" />
          </div>
        </div>
        <div>
          <p className="font-semibold text-sm tracking-tight">AspectOR</p>
          <p className="text-[11px] text-muted-foreground">OpenRouter · Admin Panel</p>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 space-y-0.5 overflow-y-auto pb-4">
        <p className="text-[10px] font-semibold text-muted-foreground/60 uppercase tracking-widest px-3 py-2">Management</p>
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} onClick={() => setSidebarOpen(false)}>
            {({ isActive }) => (
              <div className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all',
                isActive
                  ? 'text-primary-foreground shadow-sm'
                  : 'text-muted-foreground hover:bg-accent hover:text-foreground'
              )}
              style={isActive ? {background: 'var(--primary)', boxShadow: '0 2px 8px oklch(0.52 0.19 264 / 30%)'} : {}}>
                <Icon className="w-4 h-4 shrink-0" />
                {label}
              </div>
            )}
          </NavLink>
        ))}

        <div className="pt-3 mt-3 border-t border-border">
          <p className="text-[10px] font-semibold text-muted-foreground/60 uppercase tracking-widest px-3 py-2">Tools</p>
          <NavLink to="/playground" onClick={() => setSidebarOpen(false)}>
            <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground transition-all">
              <ExternalLink className="w-4 h-4 shrink-0" />
              AI Playground
            </div>
          </NavLink>
          <NavLink to="/settings" onClick={() => setSidebarOpen(false)}>
            <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground transition-all">
              <KeyRound className="w-4 h-4 shrink-0" />
              Settings &amp; API Key
            </div>
          </NavLink>
        </div>
      </nav>

      {/* User + actions */}
      <div className="px-3 py-3 border-t border-border">
        <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-muted/50 mb-2">
          <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-semibold shrink-0"
            style={{background: 'var(--primary)', color: 'var(--primary-foreground)'}}>
            {user?.email?.[0]?.toUpperCase() ?? 'A'}
          </div>
          <div className="min-w-0">
            <p className="text-xs font-medium truncate">{user?.email}</p>
            <p className="text-[11px] text-muted-foreground">Administrator</p>
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="ghost" size="icon" onClick={toggleTheme} className="flex-1" title="Toggle theme">
            {dark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </Button>
          <Button variant="ghost" size="icon" onClick={() => setChangePwOpen(true)} className="flex-1" title="Change password">
            <KeyRound className="w-4 h-4" />
          </Button>
          <Button variant="ghost" size="icon" onClick={handleLogout} className="flex-1" title="Sign out">
            <LogOut className="w-4 h-4" />
          </Button>
        </div>
      </div>
    </aside>
  )

  return (
    <>
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
              <span className="font-semibold text-sm">AspectOR</span>
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

    <ChangePasswordDialog open={changePwOpen} onOpenChange={setChangePwOpen} />
    </>
  )
}
