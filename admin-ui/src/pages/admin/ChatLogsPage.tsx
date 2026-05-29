import { useEffect, useState } from 'react'
import { adminApi } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Download, Search } from 'lucide-react'
import { formatDate } from '@/lib/utils'
import { toast } from 'sonner'

interface ChatLog {
  id: number; userEmail: string; model: string
  promptTokens: number; completionTokens: number; totalTokens: number
  latencyMs: number; statusCode: number; responsePreview: string; createdAt: string
}

export default function ChatLogsPage() {
  const [logs, setLogs] = useState<ChatLog[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [userFilter, setUserFilter] = useState('')
  const [selectedLog, setSelectedLog] = useState<ChatLog | null>(null)

  const fetchLogs = (p = 0) => {
    setLoading(true)
    adminApi.getChatLogs({ page: p, size: 20, user: userFilter || undefined })
      .then(r => {
        setLogs(r.data.content)
        setTotalPages(r.data.totalPages)
        setPage(p)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { fetchLogs() }, [])

  const handleExport = async () => {
    try {
      const r = await adminApi.exportChatLogs()
      const url = window.URL.createObjectURL(new Blob([r.data]))
      const a = document.createElement('a')
      a.href = url
      a.download = `chat-logs-${new Date().toISOString().split('T')[0]}.csv`
      a.click()
      toast.success('Export downloaded')
    } catch {
      toast.error('Export failed')
    }
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Chat Logs</h1>
        <Button variant="outline" size="sm" onClick={handleExport}>
          <Download className="w-4 h-4 mr-2" /> Export CSV
        </Button>
      </div>

      <div className="flex gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input placeholder="Filter by email..." className="pl-9"
            value={userFilter} onChange={e => setUserFilter(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && fetchLogs(0)} />
        </div>
        <Button onClick={() => fetchLogs(0)}>Search</Button>
      </div>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="text-left p-3 font-medium">User</th>
                  <th className="text-left p-3 font-medium">Model</th>
                  <th className="text-right p-3 font-medium">Tokens</th>
                  <th className="text-right p-3 font-medium">Latency</th>
                  <th className="text-left p-3 font-medium">Preview</th>
                  <th className="text-left p-3 font-medium">Date</th>
                </tr>
              </thead>
              <tbody>
                {loading ? Array(5).fill(0).map((_, i) => (
                  <tr key={i} className="border-b border-border">
                    {Array(6).fill(0).map((_, j) => (
                      <td key={j} className="p-3"><Skeleton className="h-4" /></td>
                    ))}
                  </tr>
                )) : logs.map(log => (
                  <tr key={log.id} className="border-b border-border hover:bg-muted/30 cursor-pointer"
                    onClick={() => setSelectedLog(log)}>
                    <td className="p-3 max-w-[150px] truncate">{log.userEmail}</td>
                    <td className="p-3 max-w-[120px] truncate text-xs text-muted-foreground">
                      {log.model.split('/')[1]?.replace(':free', '')}
                    </td>
                    <td className="p-3 text-right">{log.totalTokens}</td>
                    <td className="p-3 text-right">{log.latencyMs}ms</td>
                    <td className="p-3 max-w-[200px] truncate text-muted-foreground text-xs">
                      {log.responsePreview}
                    </td>
                    <td className="p-3 text-xs text-muted-foreground whitespace-nowrap">
                      {formatDate(log.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Pagination */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">Page {page + 1} of {totalPages}</p>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => fetchLogs(page - 1)}>Previous</Button>
          <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => fetchLogs(page + 1)}>Next</Button>
        </div>
      </div>

      {/* Detail dialog */}
      <Dialog open={!!selectedLog} onOpenChange={() => setSelectedLog(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Log #{selectedLog?.id}</DialogTitle>
          </DialogHeader>
          {selectedLog && (
            <div className="space-y-3 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div><span className="text-muted-foreground">User:</span> {selectedLog.userEmail}</div>
                <div><span className="text-muted-foreground">Model:</span> {selectedLog.model}</div>
                <div><span className="text-muted-foreground">Tokens:</span> {selectedLog.totalTokens}</div>
                <div><span className="text-muted-foreground">Latency:</span> {selectedLog.latencyMs}ms</div>
                <div><span className="text-muted-foreground">Status:</span> {selectedLog.statusCode}</div>
                <div><span className="text-muted-foreground">Date:</span> {formatDate(selectedLog.createdAt)}</div>
              </div>
              <div>
                <p className="text-muted-foreground mb-1">Response Preview:</p>
                <p className="bg-muted rounded-lg p-3 whitespace-pre-wrap">{selectedLog.responsePreview}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
