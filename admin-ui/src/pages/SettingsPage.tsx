import { useEffect, useState } from 'react'
import { apiKeyApi } from '@/lib/api'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { toast } from 'sonner'
import { CheckCircle2, XCircle, KeyRound, ArrowLeft, ExternalLink } from 'lucide-react'
import { ChangePasswordDialog } from '@/components/ui/change-password-dialog'
import MyModelsTab from '@/pages/MyModelsTab'

export default function SettingsPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [configured, setConfigured] = useState<boolean | null>(null)
  const [apiKey, setApiKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [removing, setRemoving] = useState(false)
  const initialTab = (['key', 'models', 'account'] as const)
    .find(t => t === searchParams.get('tab')) ?? 'key'
  const [activeTab, setActiveTab] = useState<'key' | 'models' | 'account'>(initialTab)
  const [changePwOpen, setChangePwOpen] = useState(false)

  useEffect(() => {
    apiKeyApi.getStatus()
      .then(r => setConfigured(r.data.configured))
      .catch(() => setConfigured(false))
  }, [])

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
          <p className="text-xs text-muted-foreground">Manage your OpenRouter API key and preferences</p>
        </div>
      </div>

      <div className="max-w-2xl mx-auto p-6 space-y-6">
        {/* Tabs */}
        <div className="flex gap-1 border-b border-border">
          {(['key', 'models', 'account'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab
                  ? 'border-primary text-foreground'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {tab === 'key' ? 'API Key' : tab === 'models' ? 'My Models' : 'Account'}
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
                <Button variant="destructive" size="sm" onClick={handleRemove} disabled={removing}>
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

        {/* Tab: My Models */}
        {activeTab === 'models' && <MyModelsTab />}

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
