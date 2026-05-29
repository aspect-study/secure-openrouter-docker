import { useEffect, useState } from 'react'
import { adminApi } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { modelEmoji, modelDisplayName, formatDate } from '@/lib/utils'
import { toast } from 'sonner'

interface ModelConfig {
  id: number; modelId: string; enabled: boolean
  lastUsedAt: string | null; createdAt: string
}

export default function ModelManagerPage() {
  const [models, setModels] = useState<ModelConfig[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    adminApi.getModels()
      .then(r => setModels(r.data))
      .finally(() => setLoading(false))
  }, [])

  const toggle = async (modelId: string) => {
    try {
      const r = await adminApi.toggleModel(modelId)
      setModels(prev => prev.map(m => m.modelId === modelId ? r.data : m))
      toast.success(`${modelId.split('/')[1]} ${r.data.enabled ? 'enabled' : 'disabled'}`)
    } catch {
      toast.error('Failed to update model')
    }
  }

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Model Manager</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Enable or disable free models available to users
        </p>
      </div>

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-4 space-y-3">
              {Array(6).fill(0).map((_, i) => <Skeleton key={i} className="h-16" />)}
            </div>
          ) : (
            <div className="divide-y divide-border">
              {models.map(model => (
                <div key={model.id}
                  className="flex items-center justify-between p-4 hover:bg-muted/30 transition-colors">
                  <div className="flex items-center gap-3">
                    <span className="text-2xl">{modelEmoji(model.modelId)}</span>
                    <div>
                      <p className="font-medium text-sm">{modelDisplayName(model.modelId)}</p>
                      <p className="text-xs text-muted-foreground">{model.modelId}</p>
                      {model.lastUsedAt && (
                        <p className="text-xs text-muted-foreground">
                          Last used: {formatDate(model.lastUsedAt)}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <Badge variant={model.enabled ? 'default' : 'secondary'}>
                      {model.enabled ? 'Enabled' : 'Disabled'}
                    </Badge>
                    <Switch
                      checked={model.enabled}
                      onCheckedChange={() => toggle(model.modelId)}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
