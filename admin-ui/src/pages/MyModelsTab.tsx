import { useState } from 'react'
import { userModelApi } from '@/lib/api'
import { useEffectiveModels, UserModelDto } from '@/hooks/useEffectiveModels'
import { toast } from 'sonner'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn, modelEmoji } from '@/lib/utils'

// ── Owner grouping (mirrors ModelManagerPage categorisation) ─────────────────
const OWNER_GROUPS = [
  { label: 'NVIDIA',    emoji: '🧠', prefix: 'nvidia/' },
  { label: 'Meta',      emoji: '🦙', prefix: 'meta-llama/' },
  { label: 'Google',    emoji: '💎', prefix: 'google/' },
  { label: 'OpenAI',    emoji: '⚡', prefix: 'openai/' },
  { label: 'DeepSeek',  emoji: '🌊', prefix: 'deepseek/' },
  { label: 'Qwen',      emoji: '🔮', prefix: 'qwen/' },
  { label: 'Moonshot',  emoji: '🌙', prefix: 'moonshotai/' },
  { label: 'Liquid AI', emoji: '💧', prefix: 'liquid/' },
  { label: 'Poolside',  emoji: '🏊', prefix: 'poolside/' },
  { label: 'Others',    emoji: '🤖', prefix: '' },
]
const KNOWN_PREFIXES = OWNER_GROUPS.filter(g => g.prefix).map(g => g.prefix)

type FilterTab = 'all' | 'enabled' | 'disabled'

function groupModels(models: UserModelDto[]) {
  return OWNER_GROUPS.map(owner => ({
    ...owner,
    models: models.filter(m =>
      owner.prefix
        ? m.modelId.startsWith(owner.prefix)
        : !KNOWN_PREFIXES.some(p => m.modelId.startsWith(p))
    ),
  })).filter(g => g.models.length > 0)
}

/**
 * My Models tab — lets users enable/disable individual models from their Playground.
 *
 * Displayed inside SettingsPage as the 'models' tab.
 *
 * Design invariants:
 * - Admin-disabled models are shown dimmed and their toggle is unclickable.
 * - Toggle uses optimistic UI: flip immediately, revert + toast on API failure.
 * - The all-disabled warning banner fires when totalUserEnabled === 0, derived from
 *   the shared useEffectiveModels hook — same data source as PlaygroundPage.
 * - Toggle calls PUT /api/user/models/{id}/toggle using UserModelDto.id (integer PK).
 *   The modelId string is never used in a URL path.
 */
export default function MyModelsTab() {
  const { models, totalAdminEnabled, totalUserEnabled, loading, error, refresh } =
    useEffectiveModels()

  const [filter, setFilter] = useState<FilterTab>('all')

  // Local optimistic overrides: modelConfigId → userEnabled
  // Applied on top of server state — cleared on refresh
  const [optimisticOverrides, setOptimisticOverrides] = useState<Map<number, boolean>>(
    new Map()
  )
  // Track in-flight toggle requests to prevent double-click
  const [togglingIds, setTogglingIds] = useState<Set<number>>(new Set())

  /** Derive display state for a model, merging server state with optimistic overrides */
  const getDisplayState = (model: UserModelDto) => {
    const override = optimisticOverrides.get(model.id)
    const userEnabled = override !== undefined ? override : model.userEnabled
    const effectivelyEnabled = model.adminEnabled && userEnabled
    return { userEnabled, effectivelyEnabled }
  }

  /** Optimistic toggle with revert on failure */
  const handleToggle = async (model: UserModelDto) => {
    if (!model.adminEnabled) return           // guard: unclickable if admin-disabled
    if (togglingIds.has(model.id)) return     // guard: no double-click

    const previousValue = optimisticOverrides.get(model.id) ?? model.userEnabled
    const optimisticValue = !previousValue

    // Apply optimistic update immediately
    setOptimisticOverrides(prev => new Map(prev).set(model.id, optimisticValue))
    setTogglingIds(prev => new Set(prev).add(model.id))

    try {
      await userModelApi.toggleModel(model.id)
      // Server confirmed — refresh to sync authoritative state and clear overrides
      refresh()
      setOptimisticOverrides(new Map())
    } catch {
      // Revert optimistic update
      setOptimisticOverrides(prev => {
        const next = new Map(prev)
        next.set(model.id, previousValue)
        return next
      })
      toast.error('Failed to update model preference. Please try again.')
    } finally {
      setTogglingIds(prev => {
        const next = new Set(prev)
        next.delete(model.id)
        return next
      })
    }
  }

  // Effective totalUserEnabled accounting for optimistic overrides
  const effectiveTotalUserEnabled = models.reduce((count, model) => {
    const { effectivelyEnabled } = getDisplayState(model)
    return count + (effectivelyEnabled ? 1 : 0)
  }, 0)

  // Counts for filter tabs (based on userEnabled, not effectivelyEnabled — mirrors admin UX)
  const enabledCount  = models.filter(m => getDisplayState(m).userEnabled).length
  const disabledCount = models.length - enabledCount

  // Apply filter before grouping
  const filteredModels = models.filter(m => {
    if (filter === 'all') return true
    const { userEnabled } = getDisplayState(m)
    return filter === 'enabled' ? userEnabled : !userEnabled
  })

  if (loading) {
    return <p className="text-sm text-muted-foreground py-4">Loading models…</p>
  }

  if (error) {
    return (
      <div className="flex items-center gap-3 py-4">
        <p className="text-sm text-muted-foreground flex-1">{error}</p>
        <Button variant="ghost" size="sm" onClick={refresh}>
          <RefreshCw className="w-3 h-3 mr-1" /> Retry
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div>
        <h2 className="text-sm font-semibold">My Models</h2>
        <p className="text-xs text-muted-foreground mt-0.5">
          Customize which models appear in your Playground.
          Admin-disabled models are grayed out and cannot be enabled.
        </p>
      </div>

      {/* Filter tabs + refresh */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="inline-flex rounded-lg border border-border bg-muted p-1 gap-1">
          {([
            { key: 'all'      as FilterTab, label: 'All',      count: models.length  },
            { key: 'enabled'  as FilterTab, label: 'Enabled',  count: enabledCount   },
            { key: 'disabled' as FilterTab, label: 'Disabled', count: disabledCount  },
          ]).map(tab => (
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
        <Button variant="ghost" size="sm" onClick={refresh} className="h-7 text-xs">
          <RefreshCw className="w-3 h-3 mr-1" /> Refresh
        </Button>
      </div>

      {/* All-disabled warning banner */}
      {effectiveTotalUserEnabled === 0 && (
        <div className="flex items-center gap-3 px-4 py-3 rounded-xl border border-yellow-500/30 bg-yellow-500/10 text-sm">
          <AlertTriangle className="w-4 h-4 text-yellow-500 shrink-0" />
          <span className="text-foreground">
            You've disabled all models. Enable at least one to use the Playground.
          </span>
        </div>
      )}

      {/* Model list — grouped by owner */}
      {models.length === 0 ? (
        <p className="text-sm text-muted-foreground text-center py-8">
          No models configured. Contact your admin.
        </p>
      ) : filteredModels.length === 0 ? (
        <p className="text-sm text-muted-foreground text-center py-8">
          No models in this category.
        </p>
      ) : (
        <div className="space-y-4">
          {groupModels(filteredModels).map(group => (
            <div key={group.label}>
              {/* Owner section header */}
              <div className="flex items-center gap-2 mb-2 px-1">
                <span className="text-base">{group.emoji}</span>
                <h3 className="text-sm font-semibold text-foreground">{group.label}</h3>
                <span className="text-xs text-muted-foreground">({group.models.length})</span>
                <div className="flex-1 h-px bg-border ml-1" />
              </div>

              <div className="space-y-2">
                {group.models.map(model => {
                  const { userEnabled } = getDisplayState(model)
                  const isToggling = togglingIds.has(model.id)
                  const isAdminDisabled = !model.adminEnabled

                  return (
                    <div
                      key={model.id}
                      className={cn(
                        'flex items-center justify-between px-4 py-3 rounded-xl border border-border bg-card transition-opacity',
                        isAdminDisabled && 'opacity-50'
                      )}
                    >
                      {/* Model info */}
                      <div className="flex items-center gap-3 flex-1 min-w-0">
                        <span className="text-xl shrink-0">{modelEmoji(model.modelId)}</span>
                        <div className="min-w-0 space-y-0.5">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-medium truncate">{model.name}</span>
                            {isAdminDisabled ? (
                              <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-muted text-muted-foreground shrink-0">
                                Admin Disabled
                              </span>
                            ) : (
                              <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-green-500/15 text-green-600 dark:text-green-400 shrink-0">
                                Admin Enabled
                              </span>
                            )}
                          </div>
                          <p className="text-[11px] text-muted-foreground font-mono truncate">
                            {model.modelId}
                          </p>
                        </div>
                      </div>

                      {/* Toggle switch */}
                      <button
                        role="switch"
                        aria-checked={userEnabled}
                        aria-label={`${userEnabled ? 'Disable' : 'Enable'} ${model.name}`}
                        disabled={isAdminDisabled || isToggling}
                        onClick={() => handleToggle(model)}
                        className={cn(
                          'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full border-2 border-transparent',
                          'transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
                          isAdminDisabled || isToggling
                            ? 'cursor-not-allowed opacity-60'
                            : 'cursor-pointer',
                          userEnabled ? 'bg-primary' : 'bg-input'
                        )}
                      >
                        <span
                          className={cn(
                            'pointer-events-none block h-4 w-4 rounded-full bg-background shadow-lg ring-0 transition-transform',
                            userEnabled ? 'translate-x-4' : 'translate-x-0'
                          )}
                        />
                      </button>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
