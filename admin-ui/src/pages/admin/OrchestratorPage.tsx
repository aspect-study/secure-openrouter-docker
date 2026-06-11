import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { ModelResponseCard } from '@/components/ui/model-response-card'
import { ChatMessage } from '@/components/ui/chat-message'
import { isDarkMode } from '@/lib/theme'
import { toast } from 'sonner'
import { GitMerge, Loader2, Send } from 'lucide-react'

interface ModelCard {
  modelId: string
  name: string
  content: string
  latencyMs: number
}

interface AllDoneSummary {
  successCount: number
  totalModels: number
  totalMs: number
}

interface Synthesis {
  content: string
  modelId: string
  modelName: string
}

export default function OrchestratorPage() {
  const [prompt, setPrompt] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [cards, setCards] = useState<ModelCard[]>([])
  const [allDone, setAllDone] = useState(false)
  const [summary, setSummary] = useState<AllDoneSummary | null>(null)
  const [synthesizing, setSynthesizing] = useState(false)
  const [synthesis, setSynthesis] = useState<Synthesis | null>(null)
  const [streamError, setStreamError] = useState<string | null>(null)
  const readerRef = useRef<ReadableStreamDefaultReader<Uint8Array> | null>(null)
  const dark = isDarkMode()

  const reset = () => {
    readerRef.current?.cancel()
    readerRef.current = null
    setCards([])
    setAllDone(false)
    setSummary(null)
    setSynthesis(null)
    setStreamError(null)
  }

  const handleSubmit = async () => {
    const q = prompt.trim()
    if (!q || streaming) return
    reset()
    setStreaming(true)

    const token = localStorage.getItem('token')

    try {
      const response = await fetch('/api/orchestrate/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ prompt: q }),
      })

      if (!response.ok || !response.body) {
        const body = await response.json().catch(() => ({}))
        const msg = (body as { error?: string }).error ?? 'Request failed'
        if (response.status === 409) {
          toast.error('No API key configured', {
            description: 'Go to Settings to add your OpenRouter API key.',
            duration: 8000,
          })
        } else {
          toast.error(msg)
        }
        return
      }

      const reader = response.body.getReader()
      readerRef.current = reader
      const decoder = new TextDecoder()
      let buffer = ''
      let eventName = ''
      let eventData = ''

      outer: while (true) {
        const { value, done } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop()!

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            eventData = line.slice(5).trim()
          } else if (line === '') {
            if (eventName && eventData) {
              try {
                const payload = JSON.parse(eventData)
                if (eventName === 'model_response') {
                  setCards(prev => [...prev, {
                    modelId: payload.modelId,
                    name: payload.name,
                    content: payload.content,
                    latencyMs: payload.latencyMs,
                  }])
                } else if (eventName === 'all_done') {
                  setAllDone(true)
                  setSummary({
                    successCount: payload.successCount,
                    totalModels: payload.totalModels,
                    totalMs: payload.totalMs,
                  })
                  break outer
                } else if (eventName === 'error') {
                  setStreamError(payload.error ?? 'Unknown error')
                  toast.error(payload.error ?? 'Orchestration failed')
                  break outer
                }
              } catch {
                // malformed SSE line — skip
              }
              eventName = ''
              eventData = ''
            }
          }
        }
      }
    } catch {
      toast.error('Orchestration failed. Please try again.')
    } finally {
      setStreaming(false)
    }
  }

  const handleSynthesize = async () => {
    if (cards.length === 0 || synthesizing) return
    setSynthesizing(true)
    const token = localStorage.getItem('token')

    try {
      const response = await fetch('/api/orchestrate/synthesize', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          prompt: prompt.trim(),
          responses: cards.map(c => ({
            modelId: c.modelId,
            name: c.name,
            content: c.content,
            latencyMs: c.latencyMs,
            status: 'SUCCESS',
          })),
        }),
      })

      if (!response.ok) {
        const body = await response.json().catch(() => ({}))
        toast.error((body as { error?: string }).error ?? 'Synthesis failed', { duration: 8000 })
        return
      }

      setSynthesis(await response.json() as Synthesis)
    } catch {
      toast.error('Synthesis failed. Please try again.')
    } finally {
      setSynthesizing(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-7 py-5 border-b border-border shrink-0">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-9 h-9 rounded-xl shrink-0"
            style={{ background: 'var(--primary)', color: 'var(--primary-foreground)' }}>
            <GitMerge className="w-5 h-5" />
          </div>
          <div>
            <h1 className="font-semibold text-base">Orchestrator</h1>
            <p className="text-xs text-muted-foreground">Ask all your enabled models simultaneously</p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto p-7 space-y-6">
        {/* Prompt */}
        <div className="flex gap-3">
          <Textarea
            placeholder="Ask all your models…"
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={streaming}
            rows={3}
            className="resize-none flex-1"
          />
          <Button onClick={handleSubmit} disabled={!prompt.trim() || streaming} className="self-end">
            {streaming
              ? <Loader2 className="w-4 h-4 animate-spin" />
              : <Send className="w-4 h-4" />}
          </Button>
        </div>

        {/* Streaming indicator */}
        {streaming && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="w-4 h-4 animate-spin" />
            <span>
              {cards.length > 0
                ? `Querying models · ${cards.length} responded so far…`
                : 'Querying models…'}
            </span>
          </div>
        )}

        {/* Pre-flight error (no models / rate limit) */}
        {streamError && !streaming && (
          <p className="text-sm text-destructive">{streamError}</p>
        )}

        {/* Zero success */}
        {allDone && summary && summary.successCount === 0 && (
          <p className="text-sm text-muted-foreground">No models responded. Please try again.</p>
        )}

        {/* Response cards */}
        {cards.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {cards.map(card => (
              <ModelResponseCard
                key={card.modelId}
                name={card.name}
                content={card.content}
                latencyMs={card.latencyMs}
              />
            ))}
        </div>
        )}

        {/* Summary + Synthesize button */}
        {allDone && summary && summary.successCount > 0 && (
          <div className="flex items-center gap-4 flex-wrap">
            <span className="text-sm text-muted-foreground">
              {summary.successCount} of {summary.totalModels} models responded
              · {summary.totalMs < 1000
                  ? `${summary.totalMs}ms`
                  : `${(summary.totalMs / 1000).toFixed(1)}s`}
            </span>
            {!synthesis && (
              <Button variant="outline" size="sm" onClick={handleSynthesize} disabled={synthesizing}>
                {synthesizing
                  ? <><Loader2 className="w-3.5 h-3.5 mr-1.5 animate-spin" />Synthesizing…</>
                  : <><GitMerge className="w-3.5 h-3.5 mr-1.5" />Synthesize All</>}
              </Button>
            )}
          </div>
        )}

        {/* Synthesis result */}
        {synthesis && (
          <Card>
            <CardHeader className="flex-row items-center gap-2 pb-3 pt-4 px-4 border-b border-border">
              <GitMerge className="w-4 h-4 text-primary shrink-0" />
              <span className="text-sm font-semibold">Synthesis</span>
              <Badge variant="secondary" className="text-xs ml-auto">{synthesis.modelName}</Badge>
            </CardHeader>
            <CardContent className="p-4 text-sm">
              <ChatMessage content={synthesis.content} isDark={dark} />
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
