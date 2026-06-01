import { useEffect, useState } from 'react'
import { adminApi } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { toast } from 'sonner'
import { Pencil, Check, X } from 'lucide-react'

interface LimitRow {
  id: number
  modelId: string
  userId: number | null
  maxRequestsPerDay: number
  maxTokensPerDay: number
  updatedAt: string
  editing?: boolean
  editReq?: number
  editTok?: number
}

export default function UsageLimitsPage() {
  const [limits, setLimits] = useState<LimitRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    adminApi.getGlobalLimits()
      .then(r => setLimits(r.data))
      .catch(() => toast.error('Failed to load limits'))
      .finally(() => setLoading(false))
  }, [])

  const startEdit = (id: number) => {
    setLimits(prev => prev.map(l =>
      l.id === id
        ? { ...l, editing: true, editReq: l.maxRequestsPerDay, editTok: l.maxTokensPerDay }
        : l
    ))
  }

  const cancelEdit = (id: number) => {
    setLimits(prev => prev.map(l => l.id === id ? { ...l, editing: false } : l))
  }

  const saveEdit = async (row: LimitRow) => {
    const req = row.editReq ?? row.maxRequestsPerDay
    const tok = row.editTok ?? row.maxTokensPerDay
    try {
      const r = await adminApi.setGlobalLimit(row.modelId, req, tok)
      setLimits(prev => prev.map(l =>
        l.id === row.id
          ? { ...r.data, editing: false }
          : l
      ))
      toast.success(`Limits updated for ${row.modelId.split('/').pop()}`)
    } catch {
      toast.error('Failed to update limit')
    }
  }

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Usage Limits</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Global default daily limits per model. These apply to all users unless overridden individually.
        </p>
      </div>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="text-left p-3 font-medium">Model</th>
                  <th className="text-right p-3 font-medium">Requests / day</th>
                  <th className="text-right p-3 font-medium">Tokens / day</th>
                  <th className="text-right p-3 font-medium">Updated</th>
                  <th className="p-3 w-24" />
                </tr>
              </thead>
              <tbody>
                {loading ? Array(8).fill(0).map((_, i) => (
                  <tr key={i} className="border-b border-border">
                    {Array(5).fill(0).map((_, j) => (
                      <td key={j} className="p-3"><Skeleton className="h-4" /></td>
                    ))}
                  </tr>
                )) : limits.map(row => (
                  <tr key={row.id} className="border-b border-border hover:bg-muted/30">
                    <td className="p-3 font-mono text-xs max-w-[200px] truncate">{row.modelId}</td>
                    <td className="p-3 text-right">
                      {row.editing ? (
                        <Input
                          type="number"
                          className="w-24 h-7 text-xs text-right ml-auto"
                          value={row.editReq}
                          onChange={e => setLimits(prev => prev.map(l =>
                            l.id === row.id ? { ...l, editReq: Number(e.target.value) } : l
                          ))}
                        />
                      ) : row.maxRequestsPerDay}
                    </td>
                    <td className="p-3 text-right">
                      {row.editing ? (
                        <Input
                          type="number"
                          className="w-28 h-7 text-xs text-right ml-auto"
                          value={row.editTok}
                          onChange={e => setLimits(prev => prev.map(l =>
                            l.id === row.id ? { ...l, editTok: Number(e.target.value) } : l
                          ))}
                        />
                      ) : row.maxTokensPerDay.toLocaleString()}
                    </td>
                    <td className="p-3 text-right text-xs text-muted-foreground">
                      {new Date(row.updatedAt).toLocaleDateString()}
                    </td>
                    <td className="p-3">
                      {row.editing ? (
                        <div className="flex gap-1 justify-end">
                          <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => saveEdit(row)}>
                            <Check className="w-3 h-3" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => cancelEdit(row.id)}>
                            <X className="w-3 h-3" />
                          </Button>
                        </div>
                      ) : (
                        <div className="flex justify-end">
                          <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => startEdit(row.id)}>
                            <Pencil className="w-3 h-3" />
                          </Button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {!loading && limits.length === 0 && (
        <p className="text-sm text-muted-foreground text-center py-8">
          No global limits configured. Limits will be created automatically when first set.
        </p>
      )}
    </div>
  )
}
