import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '@/hooks/useAuth'
import { authApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import { Loader2, Zap, Eye, EyeOff } from 'lucide-react'

type View = 'signin' | 'signup'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [view, setView] = useState<View>('signin')

  // Sign in state
  const [loginEmail, setLoginEmail]       = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [loginLoading, setLoginLoading]   = useState(false)

  // Sign up state
  const [regEmail, setRegEmail]         = useState('')
  const [regPassword, setRegPassword]   = useState('')
  const [regConfirm, setRegConfirm]     = useState('')
  const [regLoading, setRegLoading]     = useState(false)

  // Show/hide password
  const [showLoginPw, setShowLoginPw] = useState(false)
  const [showRegPw, setShowRegPw]     = useState(false)

  // Password strength
  const strength = regPassword.length === 0 ? null
    : regPassword.length < 8 ? 'weak'
    : regPassword.length < 12 ? 'fair'
    : /[A-Z]/.test(regPassword) && /[0-9]/.test(regPassword) && /[^A-Za-z0-9]/.test(regPassword) ? 'strong'
    : 'good'

  const strengthConfig = {
    weak:   { label: 'Too short',  color: 'bg-red-500',     width: 'w-1/4'  },
    fair:   { label: 'Fair',       color: 'bg-amber-500',   width: 'w-2/4'  },
    good:   { label: 'Good',       color: 'bg-blue-500',    width: 'w-3/4'  },
    strong: { label: 'Strong',     color: 'bg-emerald-500', width: 'w-full' },
  }

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoginLoading(true)
    try {
      const user = await login(loginEmail, loginPassword)
      toast.success('Welcome back!')
      navigate(user.role === 'ADMIN' ? '/admin/dashboard' : '/playground')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Login failed'
      toast.error(msg)
    } finally {
      setLoginLoading(false)
    }
  }

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault()
    if (regPassword !== regConfirm) { toast.error('Passwords do not match'); return }
    if (regPassword.length < 8)    { toast.error('Password must be at least 8 characters'); return }
    setRegLoading(true)
    try {
      await authApi.register(regEmail, regPassword)
      toast.success('Account created! Signing you in...')
      const user = await login(regEmail, regPassword)
      navigate(user.role === 'ADMIN' ? '/admin/dashboard' : '/playground')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Registration failed'
      toast.error(msg)
    } finally {
      setRegLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4"
      style={{ background: 'var(--background)' }}>

      <style>{`
        @keyframes fadeInSlow {
          from { opacity: 0; }
          to   { opacity: 1; }
        }
        .animate-fade-in-slow {
          animation: fadeInSlow 2s ease-in forwards;
        }
      `}</style>

      <div className="w-full max-w-md flex flex-col gap-8 mt-16">

        {/* Logo */}
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="relative">
            <div className="absolute inset-0 rounded-2xl blur-lg opacity-40" style={{ background: 'var(--primary)' }} />
            <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl text-primary-foreground shadow-lg" style={{ background: 'var(--primary)' }}>
              <Zap className="w-7 h-7" />
            </div>
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">AspectOR</h1>
            <p className="text-sm font-semibold tracking-wide text-foreground mt-1">
              Your private gateway to free LLMs
            </p>
            <p className="text-[11px] text-muted-foreground/60 mt-0.5 animate-fade-in-slow">
              Chat, manage, and monitor from one place.
            </p>
          </div>
        </div>

        {/* ── Sign In ─────────────────────────── */}
        {view === 'signin' && (
          <Card className="border-primary/30 shadow-[0_0_24px_2px_var(--primary)] shadow-primary/20">
            <CardContent className="pt-8 pb-8 px-8">
              <form onSubmit={handleLogin} className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="login-email">Email</Label>
                  <Input
                    id="login-email"
                    type="email"
                    placeholder="you@example.com"
                    value={loginEmail}
                    onChange={e => setLoginEmail(e.target.value)}
                    required
                    autoFocus
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="login-password">Password</Label>
                  <div className="relative">
                    <Input
                      id="login-password"
                      type={showLoginPw ? 'text' : 'password'}
                      placeholder="••••••••"
                      value={loginPassword}
                      onChange={e => setLoginPassword(e.target.value)}
                      required
                      className="pr-10"
                    />
                    <button
                      type="button"
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowLoginPw(!showLoginPw)}
                    >
                      {showLoginPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
                <Button type="submit" className="w-full" disabled={loginLoading}>
                  {loginLoading
                    ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Signing in...</>
                    : 'Sign In'}
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  Don't have an account?{' '}
                  <button
                    type="button"
                    onClick={() => setView('signup')}
                    className="text-primary font-medium hover:underline"
                  >
                    Sign Up
                  </button>
                </p>
              </form>
            </CardContent>
          </Card>
        )}

        {/* ── Sign Up ─────────────────────────── */}
        {view === 'signup' && (
          <Card className="border-primary/30 shadow-[0_0_24px_2px_var(--primary)] shadow-primary/20">
            <CardContent className="pt-8 pb-8 px-8">
              <form onSubmit={handleRegister} className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="reg-email">Email</Label>
                  <Input
                    id="reg-email"
                    type="email"
                    placeholder="you@example.com"
                    value={regEmail}
                    onChange={e => setRegEmail(e.target.value)}
                    required
                    autoFocus
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="reg-password">Password</Label>
                  <div className="relative">
                    <Input
                      id="reg-password"
                      type={showRegPw ? 'text' : 'password'}
                      placeholder="Min 8 characters"
                      value={regPassword}
                      onChange={e => setRegPassword(e.target.value)}
                      required
                      className="pr-10"
                    />
                    <button
                      type="button"
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowRegPw(!showRegPw)}
                    >
                      {showRegPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                  {strength && (
                    <div className="space-y-1">
                      <div className="h-1 bg-muted rounded-full overflow-hidden">
                        <div className={`h-full rounded-full transition-all ${strengthConfig[strength].color} ${strengthConfig[strength].width}`} />
                      </div>
                      <p className="text-[11px] text-muted-foreground">{strengthConfig[strength].label}</p>
                    </div>
                  )}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="reg-confirm">Confirm Password</Label>
                  <Input
                    id="reg-confirm"
                    type="password"
                    placeholder="Repeat password"
                    value={regConfirm}
                    onChange={e => setRegConfirm(e.target.value)}
                    required
                    className={regConfirm && regConfirm !== regPassword ? 'border-red-500' : ''}
                  />
                  {regConfirm && regConfirm !== regPassword && (
                    <p className="text-[11px] text-red-500">Passwords do not match</p>
                  )}
                </div>
                <Button
                  type="submit"
                  className="w-full"
                  disabled={regLoading || !regEmail || !regPassword || regPassword !== regConfirm}
                >
                  {regLoading
                    ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Creating account...</>
                    : 'Create Account'}
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  Already have an account?{' '}
                  <button
                    type="button"
                    onClick={() => setView('signin')}
                    className="text-primary font-medium hover:underline"
                  >
                    Sign In
                  </button>
                </p>
              </form>
            </CardContent>
          </Card>
        )}

      </div>
    </div>
  )
}
