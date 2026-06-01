import { useState, useEffect, useCallback } from 'react'
import { userModelApi } from '@/lib/api'

/**
 * Shape of a single model entry returned by GET /api/user/models.
 * `id` is the model_config integer PK — use this for toggle/status API calls.
 * `modelId` is the OpenRouter string ID — for display and conversation creation only;
 * never put it in a URL path (it contains forward slashes).
 */
export interface UserModelDto {
  id: number
  modelId: string
  name: string
  adminEnabled: boolean
  userEnabled: boolean
  effectivelyEnabled: boolean
}

export interface UseEffectiveModelsResult {
  /** Full model list including admin-disabled entries (shown dimmed in My Models UI) */
  models: UserModelDto[]
  /** Count of globally admin-enabled models */
  totalAdminEnabled: number
  /**
   * Count of models effectively visible to the user (admin-enabled ∩ user-enabled).
   * This is the number used for the "Showing X of Y" counter and the all-disabled check.
   */
  totalUserEnabled: number
  loading: boolean
  error: string | null
  /** Re-fetch from the server — call after a successful toggle to sync state */
  refresh: () => void
}

/**
 * Shared hook for user model preferences.
 *
 * Used by:
 *  - MyModelsTab (Settings page) — full list with admin-disabled dimming + toggle UI
 *  - PlaygroundPage — filtered to effectivelyEnabled for the model dropdown
 *
 * The empty-state condition (`totalUserEnabled === 0`) is derived from this single
 * hook so My Models and Playground both react to the same data — no duplication.
 */
export function useEffectiveModels(): UseEffectiveModelsResult {
  const [models, setModels] = useState<UserModelDto[]>([])
  const [totalAdminEnabled, setTotalAdminEnabled] = useState(0)
  const [totalUserEnabled, setTotalUserEnabled] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(() => {
    setLoading(true)
    setError(null)
    userModelApi.getModels()
      .then(r => {
        setModels(r.data.models ?? [])
        setTotalAdminEnabled(r.data.totalAdminEnabled ?? 0)
        setTotalUserEnabled(r.data.totalUserEnabled ?? 0)
      })
      .catch(() => {
        setError('Failed to load models')
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetch()
  }, [fetch])

  return { models, totalAdminEnabled, totalUserEnabled, loading, error, refresh: fetch }
}
