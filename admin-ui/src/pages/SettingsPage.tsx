import { useEffect, useState, useCallback } from 'react'
import { apiKeyApi, usageApi } from '@/lib/api'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { toast } from 'sonner'
import { CheckCircle2, XCircle, KeyRound, RefreshCw, ArrowLeft, ExternalLink } from 'lucide-react'
import { ChangePasswordDialog } from '@/components/ui/change-password-dialog'

interface ModelUsage {
  modelId: string
  requests: number
  maxRequests: number
  tokens: number
  maxTokens: number
  requestsRemaining: number
  tokensRemaining: number
  resetAt: string
}

interface UsageSummary {
  date: string
  resetAt: string
  globalAggregate: { totalRequests: number; totalTokens: number }
  models: ModelUsage[]
}

/** Progress bar: green < 70%, yellow 70–90%, red > 90% */
function UsageBar({ value, max }: { value: number; max: number }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  const color = pct >= 90 ? 'bg-red-500' : pct >= 70 ? 'bg-yellow-500' : 'bg-green-500'
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs text-muted-foreground">
        <span>{value.toLocaleString()} / {max.toLocaleString()}</span>
        <span>{pct}%</span>
      </div>
      <div className="h-1.5 bg-muted rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-all ${color}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}

/** "Resets in X h Y m" from ISO resetAt string */
function ResetCountdown({ resetAt }: { resetAt: string }) {
  const ms = new Date(resetAt).getTime() - Date.now()
  if (ms <= 0) return <span className="text-xs text-muted-foreground">Resets soon</span>
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  return <span className="text-xs text-muted-foreground">Resets in {h}h {m}m</span>
}

export default function SettingsPage() {
  const navigate = useNavigate()
  const [configured, setConfigured] = useState<boolean | null>(null)
  const [apiKey, setApiKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [usage, setUsage] = useState<UsageSummary | null>(null)
  const [usageLoading, setUsageLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'key' | 'usage' | 'account'>('key')
  const [changePwOpen, setChangePwOpen] = useState(false)

  // Load key status on mount
  useEffect(() => {
    apiKeyApi.getStatus()
      .then(r => setConfigured(r.data.configured))
      .catch(() => setConfigured(false))
  }, [])

  const loadUsage = useCallback(() => {
    setUsageLoading(true)
    usageApi.getTodayUsage()
      .then(r => setUsage(r.data))
      .catch(() => toast.error('Failed to load usage'))
      .finally(() => setUsageLoading(false))
  }, [])

  useEffect(() => {
    if (activeTab === 'usage') loadUsage()
  }, [activeTab, loadUsage])

  const handleSave = async () => {
    if (!apiKey.trim()) return
    setSaving(true)
    try {
      await apiKeyApi.saveKey(apiKey.trim())
      setConfigured(true)
      setApiKey('')
      toast.success('API key saved and validated')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
        ?? 'Failed to save API key'
      toast.error(msg)
    } finally {
      setSaving(false)
    }
  }

  const handleRemove = async () => {
    setRemoving(true)
    try {
      await apiKeyApi.removeKey()
      setConfigured(false)
      toast.success('API key removed')
    } catch {
      toast.error('Failed to remove API key')
    } finally {
      setRemoving(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <div className="border-b border-border px-6 py-4 flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
          <ArrowLeft className="w-4 h-4" />
        </Button>
        <div>
          <h1 className="text-lg font-semibold">Settings</h1>
          <p className="text-xs text-muted-foreground">Manage your OpenRouter API key and usage</p>
        </div>
      </div>

      <div className="max-w-2xl mx-auto p-6 space-y-6">
        {/* Tabs */}
        <div className="flex gap-1 border-b border-border">
          {(['key', 'usage', 'account'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab
                  ? 'border-primary text-foreground'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {tab === 'key' ? 'API Key' : tab === 'usage' ? 'Usage Dashboard' : 'Account'}
            </button>
          ))}
        </div>

        {/* Tab: API Key */}
        {activeTab === 'key' && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <KeyRound className="w-4 h-4" />
                OpenRouter API Key
              </CardTitle>
              <CardDescription>
                Your key is used exclusively for your chat requests.
                It's stored encrypted and never visible after saving.{' '}
                <a
                  href="https://openrouter.ai/keys"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-primary hover:underline"
                >
                  Get your free key <ExternalLink className="w-3 h-3" />
                </a>
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Status */}
              <div className="flex items-center gap-2">
                {configured === null ? (
                  <span className="text-sm text-muted-foreground">Checking…</span>
                ) : configured ? (
                  <>
                    <CheckCircle2 className="w-4 h-4 text-green-500" />
                    <span className="text-sm text-green-600 dark:text-green-400 font-medium">Key configured</span>
                  </>
                ) : (
                  <>
                    <XCircle className="w-4 h-4 text-muted-foreground" />
                    <span className="text-sm text-muted-foreground">No key set</span>
                  </>
                )}
              </div>

              {/* Input */}
              <div className="flex gap-2">
                <Input
                  type="password"
                  placeholder="sk-or-v1-..."
                  value={apiKey}
                  onChange={e => setApiKey(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleSave()}
                  className="font-mono text-sm"
                />
                <Button onClick={handleSave} disabled={saving || !apiKey.trim()}>
                  {saving ? 'Validating…' : configured ? 'Update' : 'Save'}
                </Button>
              </div>

              {configured && (
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={handleRemove}
                  disabled={removing}
                >
                  {removing ? 'Removing…' : 'Remove Key'}
                </Button>
              )}

              <p className="text-xs text-muted-foreground">
                Your key is validated against OpenRouter before saving.
                OpenRouter accounts are free and take under 2 minutes to create.
              </p>
            </CardContent>
          </Card>
        )}

        {/* Tab: Usage Dashboard */}
        {activeTab === 'usage' && (
          <div className="space-y-4">
            {/* Aggregate card */}
            {usage && (
              <Card>
                <CardContent className="pt-4">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium">Today's Usage</span>
                    <ResetCountdown resetAt={usage.resetAt} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {usage.globalAggregate.totalRequests} requests · {usage.globalAggregate.totalTokens.toLocaleString()} tokens across all models
                  </p>
                </CardContent>
              </Card>
            )}

            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold">Per-Model Breakdown</h2>
              <Button variant="ghost" size="sm" onClick={loadUsage} disabled={usageLoading}>
                <RefreshCw className={`w-3 h-3 mr-1 ${usageLoading ? 'animate-spin' : ''}`} />
                Refresh
              </Button>
            </div>

            {usageLoading && !usage && (
              <p className="text-sm text-muted-foreground">Loading…</p>
            )}

            {usage && usage.models.length === 0 && (
              <Card>
                <CardContent className="pt-6 text-center text-sm text-muted-foreground">
                  No requests made today. Limits reset daily at midnight UTC.
                </CardContent>
              </Card>
            )}

            {usage?.models.map(m => (
              <Card key={m.modelId}>
                <CardContent className="pt-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-mono font-medium truncate">{m.modelId}</span>
                    <ResetCountdown resetAt={m.resetAt} />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground mb-1">Requests</p>
                    <UsageBar value={m.requests} max={m.maxRequests} />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground mb-1">Tokens</p>
                    <UsageBar value={m.tokens} max={m.maxTokens} />
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}

        {/* Tab: Account */}
        {activeTab === 'account' && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Account</CardTitle>
              <CardDescription>Manage your password and account security.</CardDescription>
            </CardHeader>
            <CardContent>
              <Button variant="outline" onClick={() => setChangePwOpen(true)}>
                Change Password
              </Button>
            </CardContent>
          </Card>
        )}
      </div>

      <ChangePasswordDialog open={changePwOpen} onOpenChange={setChangePwOpen} />
    </div>
  )
}
