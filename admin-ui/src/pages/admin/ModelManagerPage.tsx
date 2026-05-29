import { useEffect, useState } from 'react'
import { adminApi } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { modelEmoji, modelDisplayName, modelInfo, formatDate } from '@/lib/utils'
import type { ModelInfo } from '@/lib/utils'
import { toast } from 'sonner'
import { ChevronDown, ChevronUp, Clock, Zap, AlertTriangle, CheckCircle, ThumbsUp, ThumbsDown } from 'lucide-react'

interface ModelConfig {
  id: number; modelId: string; enabled: boolean
  lastUsedAt: string | null; createdAt: string
}

function InfoRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex gap-3">
      <div className="shrink-0 mt-0.5 text-muted-foreground">{icon}</div>
      <div className="min-w-0">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
        <p className="text-xs text-foreground mt-0.5 leading-relaxed">{value}</p>
      </div>
    </div>
  )
}

function TagList({ items, color }: { items: string[]; color: 'green' | 'red' | 'blue' }) {
  const cls = {
    green: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
    red:   'bg-red-500/10 text-red-600 dark:text-red-400',
    blue:  'bg-blue-500/10 text-blue-600 dark:text-blue-400',
  }[color]
  return (
    <div className="flex flex-wrap gap-1.5 mt-1">
      {items.map((item, i) => (
        <span key={i} className={`text-[11px] px-2 py-0.5 rounded-full ${cls}`}>{item}</span>
      ))}
    </div>
  )
}

export default function ModelManagerPage() {
  const [models, setModels] = useState<ModelConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState<string | null>(null)

  useEffect(() => {
    adminApi.getModels()
      .then(r => setModels(r.data))
      .finally(() => setLoading(false))
  }, [])

  const toggle = async (modelId: string) => {
    try {
      const r = await adminApi.toggleModel(modelId)
      setModels(prev => prev.map(m => m.modelId === modelId ? r.data : m))
      toast.success(`${modelDisplayName(modelId)} ${r.data.enabled ? 'enabled' : 'disabled'}`)
    } catch {
      toast.error('Failed to update model')
    }
  }

  const toggleExpand = (modelId: string) => {
    setExpanded(prev => prev === modelId ? null : modelId)
  }

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Model Manager</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Enable or disable free models. Click a model to view full details.
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
              {models.map(model => {
                const info: ModelInfo = modelInfo(model.modelId)
                const isExpanded = expanded === model.modelId

                return (
                  <div key={model.id}>
                    {/* Row */}
                    <div
                      className="flex items-center justify-between p-4 hover:bg-muted/30 transition-colors cursor-pointer"
                      onClick={() => toggleExpand(model.modelId)}
                    >
                      <div className="flex items-center gap-3 min-w-0 flex-1">
                        <span className="text-2xl shrink-0">{modelEmoji(model.modelId)}</span>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <p className="font-medium text-sm">{modelDisplayName(model.modelId)}</p>
                            <Badge variant={model.enabled ? 'default' : 'secondary'} className="text-xs">
                              {model.enabled ? 'Enabled' : 'Disabled'}
                            </Badge>
                            <span className="text-[11px] text-muted-foreground bg-muted px-1.5 py-0.5 rounded-full">
                              {info.context}
                            </span>
                            <span className="text-[11px] text-muted-foreground bg-muted px-1.5 py-0.5 rounded-full">
                              {info.rpm}
                            </span>
                          </div>
                          <p className="text-xs text-muted-foreground mt-0.5 truncate max-w-md">
                            {info.description.split('.')[0]}.
                          </p>
                          {model.lastUsedAt && (
                            <p className="text-[11px] text-muted-foreground mt-0.5">
                              Last used: {formatDate(model.lastUsedAt)}
                            </p>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center gap-3 shrink-0 ml-4">
                        <Switch
                          checked={model.enabled}
                          onCheckedChange={() => toggle(model.modelId)}
                          onClick={e => e.stopPropagation()}
                        />
                        {isExpanded
                          ? <ChevronUp className="w-4 h-4 text-muted-foreground" />
                          : <ChevronDown className="w-4 h-4 text-muted-foreground" />
                        }
                      </div>
                    </div>

                    {/* Expanded detail panel */}
                    {isExpanded && (
                      <div className="bg-muted/20 border-t border-border px-6 py-5 space-y-5">
                        {/* Description */}
                        <p className="text-sm text-foreground leading-relaxed">{info.description}</p>

                        {/* Free usage highlight */}
                        <div className="flex items-center gap-2 text-xs bg-blue-500/10 text-blue-600 dark:text-blue-400 px-3 py-2 rounded-lg w-fit">
                          <Clock className="w-3.5 h-3.5 shrink-0" />
                          <span><strong>Free usage:</strong> {info.freeUsage}</span>
                        </div>

                        {/* 5W's */}
                        <div>
                          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">The 5 W's</p>
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <InfoRow icon={<span className="text-sm">👤</span>} label="Who" value={info.who} />
                            <InfoRow icon={<span className="text-sm">📌</span>} label="What" value={info.what} />
                            <InfoRow icon={<span className="text-sm">⏰</span>} label="When" value={info.when} />
                            <InfoRow icon={<span className="text-sm">📍</span>} label="Where" value={info.where} />
                            <InfoRow icon={<span className="text-sm">💡</span>} label="Why" value={info.why} />
                          </div>
                        </div>

                        {/* Strengths + Limitations */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                          <div>
                            <div className="flex items-center gap-1.5 mb-2">
                              <Zap className="w-3.5 h-3.5 text-emerald-500" />
                              <p className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">Strengths</p>
                            </div>
                            <TagList items={info.strengths} color="green" />
                          </div>
                          <div>
                            <div className="flex items-center gap-1.5 mb-2">
                              <AlertTriangle className="w-3.5 h-3.5 text-amber-500" />
                              <p className="text-xs font-semibold text-amber-600 dark:text-amber-400 uppercase tracking-wider">Limitations</p>
                            </div>
                            <TagList items={info.limitations} color="red" />
                          </div>
                        </div>

                        {/* Advantages + Disadvantages */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                          <div>
                            <div className="flex items-center gap-1.5 mb-2">
                              <ThumbsUp className="w-3.5 h-3.5 text-blue-500" />
                              <p className="text-xs font-semibold text-blue-600 dark:text-blue-400 uppercase tracking-wider">Advantages</p>
                            </div>
                            <TagList items={info.advantages} color="blue" />
                          </div>
                          <div>
                            <div className="flex items-center gap-1.5 mb-2">
                              <ThumbsDown className="w-3.5 h-3.5 text-muted-foreground" />
                              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Disadvantages</p>
                            </div>
                            <TagList items={info.disadvantages} color="red" />
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
