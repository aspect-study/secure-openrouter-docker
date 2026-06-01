import { useEffect, useState } from 'react'
import { adminApi } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { modelEmoji, modelDisplayName, modelInfo, formatDate } from '@/lib/utils'
import type { ModelInfo } from '@/lib/utils'
import { toast } from 'sonner'
import { ChevronDown, ChevronUp, Clock, Zap, AlertTriangle, ThumbsUp, ThumbsDown, Cpu, BarChart2 } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ModelConfig {
  id: number; modelId: string; enabled: boolean
  lastUsedAt: string | null; createdAt: string
}

// ── Owner classification ──────────────────────────────────────────────────

const OWNER_GROUPS = [
  { label: 'NVIDIA',     emoji: '🧠', prefix: 'nvidia/' },
  { label: 'Meta',       emoji: '🦙', prefix: 'meta-llama/' },
  { label: 'Google',     emoji: '💎', prefix: 'google/' },
  { label: 'OpenAI',     emoji: '⚡', prefix: 'openai/' },
  { label: 'DeepSeek',   emoji: '🌊', prefix: 'deepseek/' },
  { label: 'Qwen',       emoji: '🔮', prefix: 'qwen/' },
  { label: 'Moonshot',   emoji: '🌙', prefix: 'moonshotai/' },
  { label: 'Liquid AI',  emoji: '💧', prefix: 'liquid/' },
  { label: 'Poolside',   emoji: '🏊', prefix: 'poolside/' },
  { label: 'Others',     emoji: '🤖', prefix: '' },
]

const KNOWN_PREFIXES = OWNER_GROUPS.filter(g => g.prefix).map(g => g.prefix)

function ownerOf(modelId: string) {
  const group = OWNER_GROUPS.find(g =>
    g.prefix ? modelId.startsWith(g.prefix) : !KNOWN_PREFIXES.some(p => modelId.startsWith(p))
  )
  return group ?? OWNER_GROUPS[OWNER_GROUPS.length - 1]
}

// ── Sub-components ────────────────────────────────────────────────────────

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

type FilterTab = 'all' | 'enabled' | 'disabled'

// ── Main page ─────────────────────────────────────────────────────────────

export default function ModelManagerPage() {
  const [models, setModels] = useState<ModelConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [filter, setFilter] = useState<FilterTab>('all')

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

  const toggleExpand = (modelId: string) =>
    setExpanded(prev => prev === modelId ? null : modelId)

  // Apply enabled/disabled filter
  const filtered = models.filter(m =>
    filter === 'all' ? true : filter === 'enabled' ? m.enabled : !m.enabled
  )

  // Group by owner
  const groups = OWNER_GROUPS.map(owner => ({
    ...owner,
    models: filtered.filter(m =>
      owner.prefix
        ? m.modelId.startsWith(owner.prefix)
        : !KNOWN_PREFIXES.some(p => m.modelId.startsWith(p))
    ),
  })).filter(g => g.models.length > 0)

  const enabledCount  = models.filter(m => m.enabled).length
  const disabledCount = models.filter(m => !m.enabled).length

  const tabs: { key: FilterTab; label: string; count: number }[] = [
    { key: 'all',      label: 'All',      count: models.length },
    { key: 'enabled',  label: 'Enabled',  count: enabledCount },
    { key: 'disabled', label: 'Disabled', count: disabledCount },
  ]

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Model Manager</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Enable or disable free models, grouped by owner. Click a model to view full details.
        </p>
      </div>

      {/* Filter toggle */}
      <div className="flex items-center gap-3">
        <div className="inline-flex rounded-lg border border-border bg-muted p-1 gap-1">
          {tabs.map(tab => (
            <button
              key={tab.key}
              onClick={() => setFilter(tab.key)}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-all',
                filter === tab.key
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              )}
            >
              {tab.key === 'enabled' && (
                <span className="w-2 h-2 rounded-full bg-green-500 shrink-0" />
              )}
              {tab.key === 'disabled' && (
                <span className="w-2 h-2 rounded-full bg-muted-foreground shrink-0" />
              )}
              {tab.label}
              <span className={cn(
                'text-xs px-1.5 py-0.5 rounded-full font-medium min-w-[20px] text-center',
                filter === tab.key
                  ? tab.key === 'enabled'  ? 'bg-green-500/15 text-green-600 dark:text-green-400'
                  : tab.key === 'disabled' ? 'bg-muted-foreground/20 text-muted-foreground'
                  :                         'bg-primary/15 text-primary'
                  : 'bg-transparent text-muted-foreground'
              )}>
                {tab.count}
              </span>
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <Card>
          <CardContent className="p-4 space-y-3">
            {Array(6).fill(0).map((_, i) => <Skeleton key={i} className="h-16" />)}
          </CardContent>
        </Card>
      ) : groups.length === 0 ? (
        <p className="text-sm text-muted-foreground text-center py-12">No models in this category.</p>
      ) : (
        <div className="space-y-4">
          {groups.map(group => (
            <div key={group.label}>
              {/* Owner header */}
              <div className="flex items-center gap-2 mb-2 px-1">
                <span className="text-base">{group.emoji}</span>
                <h2 className="text-sm font-semibold text-foreground">{group.label}</h2>
                <span className="text-xs text-muted-foreground">({group.models.length})</span>
                <div className="flex-1 h-px bg-border ml-1" />
              </div>

              <Card>
                <CardContent className="p-0">
                  <div className="divide-y divide-border">
                    {group.models.map(model => {
                      const info: ModelInfo = modelInfo(model.modelId)
                      const isExpanded = expanded === model.modelId

                      return (
                        <div key={model.id}>
                          {/* Row */}
                          <div
                            className="flex items-center justify-between p-4 hover:bg-muted/30 active:bg-muted/50 transition-colors cursor-pointer"
                            onClick={() => toggleExpand(model.modelId)}
                          >
                            <div className="flex items-center gap-3 min-w-0 flex-1">
                              <span className="text-2xl shrink-0">{modelEmoji(model.modelId)}</span>
                              <div className="min-w-0">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <p className={cn('font-medium text-sm', !model.enabled && 'text-muted-foreground')}>{modelDisplayName(model.modelId)}</p>
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
                              <button
                                onClick={e => { e.stopPropagation(); toggle(model.modelId) }}
                                className={cn(
                                  'relative inline-flex items-center w-14 h-7 rounded-full transition-colors duration-200 focus:outline-none shrink-0',
                                  model.enabled ? 'bg-green-500' : 'bg-muted-foreground/30'
                                )}
                              >
                                <span className={cn(
                                  'inline-flex items-center justify-center w-5 h-5 rounded-full bg-white shadow-sm text-[9px] font-bold transition-transform duration-200',
                                  model.enabled ? 'translate-x-8 text-green-600' : 'translate-x-1 text-muted-foreground'
                                )}>
                                  {model.enabled ? 'ON' : 'OFF'}
                                </span>
                              </button>
                              {isExpanded
                                ? <ChevronUp className="w-4 h-4 text-muted-foreground" />
                                : <ChevronDown className="w-4 h-4 text-muted-foreground" />
                              }
                            </div>
                          </div>

                          {/* Expanded detail panel */}
                          {isExpanded && (
                            <div className="border-t border-border bg-muted/10">
                              <div className="px-6 py-5 space-y-6">

                                {/* Description + stats row */}
                                <div className="flex flex-col sm:flex-row gap-4">
                                  <p className="text-sm text-foreground leading-relaxed flex-1">{info.description}</p>
                                  <div className="flex sm:flex-col gap-2 shrink-0">
                                    <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-3 py-2 text-xs">
                                      <Cpu className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                                      <div>
                                        <p className="text-muted-foreground leading-none mb-0.5">Context</p>
                                        <p className="font-semibold text-foreground">{info.context}</p>
                                      </div>
                                    </div>
                                    <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-3 py-2 text-xs">
                                      <BarChart2 className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                                      <div>
                                        <p className="text-muted-foreground leading-none mb-0.5">Rate limit</p>
                                        <p className="font-semibold text-foreground">{info.rpm}</p>
                                      </div>
                                    </div>
                                  </div>
                                </div>

                                {/* Free usage pill */}
                                <div className="flex items-center gap-2 text-xs bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 px-3 py-2 rounded-lg w-fit">
                                  <Clock className="w-3.5 h-3.5 shrink-0" />
                                  <span><span className="font-semibold">Free usage:</span> {info.freeUsage}</span>
                                </div>

                                {/* 5 W's */}
                                <div>
                                  <p className="text-[10px] font-bold text-muted-foreground/60 uppercase tracking-widest mb-3">The 5 W's</p>
                                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                                    {[
                                      { emoji: '👤', label: 'Who',   value: info.who   },
                                      { emoji: '📌', label: 'What',  value: info.what  },
                                      { emoji: '⏰', label: 'When',  value: info.when  },
                                      { emoji: '📍', label: 'Where', value: info.where },
                                      { emoji: '💡', label: 'Why',   value: info.why   },
                                    ].map(({ emoji, label, value }) => (
                                      <div key={label} className="bg-background border border-border rounded-lg px-3 py-2.5">
                                        <p className="text-[10px] font-bold text-muted-foreground/60 uppercase tracking-widest mb-1">
                                          {emoji} {label}
                                        </p>
                                        <p className="text-xs text-foreground leading-relaxed">{value}</p>
                                      </div>
                                    ))}
                                  </div>
                                </div>

                                {/* Strengths / Limitations / Advantages / Disadvantages */}
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                  {[
                                    { icon: <Zap className="w-3.5 h-3.5" />, label: 'Strengths',     items: info.strengths,     color: 'green' as const, iconColor: 'text-emerald-500' },
                                    { icon: <AlertTriangle className="w-3.5 h-3.5" />, label: 'Limitations', items: info.limitations,   color: 'red'   as const, iconColor: 'text-amber-500'   },
                                    { icon: <ThumbsUp className="w-3.5 h-3.5" />,  label: 'Advantages',   items: info.advantages,    color: 'blue'  as const, iconColor: 'text-blue-500'    },
                                    { icon: <ThumbsDown className="w-3.5 h-3.5" />, label: 'Disadvantages',items: info.disadvantages, color: 'red'   as const, iconColor: 'text-muted-foreground' },
                                  ].map(({ icon, label, items, color, iconColor }) => (
                                    <div key={label} className="bg-background border border-border rounded-lg px-3 py-3">
                                      <div className={cn('flex items-center gap-1.5 mb-2.5', iconColor)}>
                                        {icon}
                                        <p className="text-[10px] font-bold uppercase tracking-widest">{label}</p>
                                      </div>
                                      <TagList items={items} color={color} />
                                    </div>
                                  ))}
                                </div>

                              </div>
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </CardContent>
              </Card>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
