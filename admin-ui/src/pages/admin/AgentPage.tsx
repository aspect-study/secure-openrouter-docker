import { useEffect, useRef, useState } from 'react'
import { agentApi, chatApi, type AgentToolStep } from '@/lib/api'
import { ChatMessage } from '@/components/ui/chat-message'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { toast } from 'sonner'
import { Bot, Send, ChevronDown, ChevronRight, Loader2 } from 'lucide-react'
import { isDarkMode } from '@/lib/theme'

interface AgentMessage {
  role: 'user' | 'agent'
  content: string
  toolSteps?: AgentToolStep[]
}

interface ModelsResponse {
  models: string[]
}

function ToolStepBlock({ step }: { step: AgentToolStep }) {
  const [inputOpen, setInputOpen] = useState(false)
  const [resultOpen, setResultOpen] = useState(false)

  return (
    <div className="rounded-lg border border-border bg-muted/30 overflow-hidden text-xs">
      <div className="flex items-center gap-2 px-3 py-2 bg-muted/50">
        <Badge variant="secondary" className="text-[10px] font-mono px-1.5 py-0">
          {step.toolName}
        </Badge>
      </div>
      <div className="divide-y divide-border">
        <button
          onClick={() => setInputOpen(v => !v)}
          className="w-full flex items-center gap-1.5 px-3 py-1.5 text-muted-foreground hover:text-foreground hover:bg-muted/40 transition-colors text-left"
        >
          {inputOpen ? <ChevronDown className="w-3 h-3 shrink-0" /> : <ChevronRight className="w-3 h-3 shrink-0" />}
          <span className="font-medium">Input</span>
        </button>
        {inputOpen && (
          <pre className="px-3 py-2 overflow-x-auto text-[11px] leading-relaxed font-mono text-foreground/80 bg-muted/20">
            {JSON.stringify(step.input, null, 2)}
          </pre>
        )}
        <button
          onClick={() => setResultOpen(v => !v)}
          className="w-full flex items-center gap-1.5 px-3 py-1.5 text-muted-foreground hover:text-foreground hover:bg-muted/40 transition-colors text-left"
        >
          {resultOpen ? <ChevronDown className="w-3 h-3 shrink-0" /> : <ChevronRight className="w-3 h-3 shrink-0" />}
          <span className="font-medium">Result</span>
        </button>
        {resultOpen && (
          <pre className="px-3 py-2 overflow-x-auto text-[11px] leading-relaxed font-mono text-foreground/80 bg-muted/20">
            {JSON.stringify(step.result, null, 2)}
          </pre>
        )}
      </div>
    </div>
  )
}

function ToolCallsSection({ steps }: { steps: AgentToolStep[] }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="mt-2">
      <button
        onClick={() => setOpen(v => !v)}
        className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
      >
        {open ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
        <span>{steps.length} tool call{steps.length !== 1 ? 's' : ''}</span>
      </button>
      {open && (
        <div className="mt-2 space-y-2">
          {steps.map((step, i) => (
            <ToolStepBlock key={i} step={step} />
          ))}
        </div>
      )}
    </div>
  )
}

export default function AgentPage() {
  const [messages, setMessages] = useState<AgentMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [models, setModels] = useState<string[]>([])
  const [selectedModel, setSelectedModel] = useState<string>('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const dark = isDarkMode()

  useEffect(() => {
    chatApi.getModels()
      .then(r => {
        const list: string[] = (r.data as ModelsResponse).models ?? []
        setModels(list)
        if (list.length > 0 && !selectedModel) {
          setSelectedModel(list[0])
        }
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  const sendMessage = async () => {
    const question = input.trim()
    if (!question || loading) return

    setMessages(prev => [...prev, { role: 'user', content: question }])
    setInput('')
    setLoading(true)

    // Reset textarea height
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }

    try {
      const res = await agentApi.chat(question, selectedModel || undefined)
      const { reply, toolSteps } = res.data
      setMessages(prev => [...prev, { role: 'agent', content: reply, toolSteps }])
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 409) {
        toast.error('No API key configured', {
          description: 'The agent requires your OpenRouter API key. Go to Settings to add it.',
          duration: 8000,
        })
      } else if (status === 403) {
        toast.error('Admin access required')
      } else {
        toast.error('Agent request failed. Please try again.')
      }
      setMessages(prev => prev.slice(0, -1))
      setInput(question)
    } finally {
      setLoading(false)
      textareaRef.current?.focus()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Page header */}
      <div className="px-7 py-5 border-b border-border shrink-0">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-9 h-9 rounded-xl shrink-0"
            style={{ background: 'var(--primary)', color: 'var(--primary-foreground)' }}>
            <Bot className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight">Gateway Agent</h1>
            <p className="text-sm text-muted-foreground">
              Ask questions about your gateway — model status, usage stats, and more.
            </p>
          </div>
        </div>
      </div>

      {/* Message area */}
      <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
        {messages.length === 0 && !loading ? (
          <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
            <div className="flex items-center justify-center w-16 h-16 rounded-2xl"
              style={{ background: 'oklch(from var(--primary) l c h / 12%)' }}>
              <Bot className="w-8 h-8" style={{ color: 'var(--primary)' }} />
            </div>
            <div className="space-y-1.5">
              <h2 className="text-lg font-semibold">Ask me anything about your gateway</h2>
              <p className="text-sm text-muted-foreground max-w-sm">
                I can check model status, query usage statistics, list active users, and more.
              </p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full max-w-md mt-2">
              {[
                'Which models are currently enabled?',
                'How many requests were made today?',
                'What are the top models by usage?',
                'Show me the gateway stats',
              ].map(prompt => (
                <button
                  key={prompt}
                  onClick={() => { setInput(prompt); textareaRef.current?.focus() }}
                  className="text-left px-4 py-3 rounded-xl border border-border bg-card hover:bg-accent hover:border-primary/30 transition-all text-sm text-muted-foreground hover:text-foreground shadow-sm"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <>
            {messages.map((msg, i) => (
              <div key={i} className={cn('flex', msg.role === 'user' ? 'justify-end' : 'justify-start')}>
                {msg.role === 'agent' && (
                  <div className="flex items-center justify-center w-7 h-7 rounded-lg mr-2 mt-1 shrink-0"
                    style={{ background: 'var(--primary)', color: 'var(--primary-foreground)' }}>
                    <Bot className="w-4 h-4" />
                  </div>
                )}
                <div className={cn(msg.role === 'user' ? 'max-w-[70%]' : 'max-w-[85%]')}>
                  <div className={cn(
                    'rounded-2xl px-4 py-3 text-sm shadow-sm',
                    msg.role === 'user'
                      ? 'rounded-br-sm text-primary-foreground'
                      : 'rounded-bl-sm bg-card border border-border text-foreground'
                  )}
                  style={msg.role === 'user' ? {
                    background: 'linear-gradient(135deg, var(--primary) 0%, oklch(from var(--primary) calc(l - 0.05) calc(c + 0.02) calc(h - 5)) 100%)',
                    boxShadow: '0 2px 12px oklch(from var(--primary) l c h / 25%)',
                  } : {}}>
                    {msg.role === 'user'
                      ? <p className="leading-relaxed whitespace-pre-wrap">{msg.content}</p>
                      : <ChatMessage content={msg.content} isDark={dark} />
                    }
                  </div>
                  {msg.role === 'agent' && msg.toolSteps && msg.toolSteps.length > 0 && (
                    <div className="mt-1 px-1">
                      <ToolCallsSection steps={msg.toolSteps} />
                    </div>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div className="flex items-start gap-2">
                <div className="flex items-center justify-center w-7 h-7 rounded-lg shrink-0"
                  style={{ background: 'var(--primary)', color: 'var(--primary-foreground)' }}>
                  <Bot className="w-4 h-4" />
                </div>
                <div className="bg-card border border-border rounded-2xl rounded-bl-sm px-4 py-3 flex items-center gap-2 text-sm text-muted-foreground shadow-sm">
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Thinking…</span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </>
        )}
      </div>

      {/* Input area */}
      <div className="border-t border-border px-4 py-4 shrink-0">
        <div className="max-w-4xl mx-auto">
          <div
            className="relative flex flex-col rounded-2xl border border-border bg-card transition-shadow focus-within:shadow-[0_0_0_2px_var(--ring),0_4px_16px_oklch(from_var(--primary)_l_c_h_/_12%)]"
            style={{ boxShadow: '0 2px 8px oklch(0 0 0 / 6%)' }}
          >
            <Textarea
              ref={textareaRef}
              placeholder="Ask the agent… (Enter to send · Shift+Enter for new line)"
              value={input}
              onChange={e => {
                setInput(e.target.value)
                e.target.style.height = 'auto'
                e.target.style.height = Math.min(e.target.scrollHeight, 200) + 'px'
              }}
              onKeyDown={handleKeyDown}
              className="min-h-[72px] max-h-[200px] resize-none border-0 shadow-none bg-transparent px-4 pt-3 pb-12 text-sm leading-relaxed focus-visible:ring-0 focus-visible:outline-none"
              rows={2}
            />
            <div className="absolute bottom-0 left-0 right-0 flex items-center justify-between px-3 py-2 border-t border-border/50">
              <div className="flex items-center gap-2">
                {models.length > 0 && (
                  <select
                    value={selectedModel}
                    onChange={e => setSelectedModel(e.target.value)}
                    className="text-xs text-muted-foreground bg-transparent border-0 outline-none cursor-pointer hover:text-foreground transition-colors max-w-[180px] truncate"
                  >
                    {models.map(m => (
                      <option key={m} value={m}>
                        {m.split('/').pop()?.replace(':free', '') ?? m}
                      </option>
                    ))}
                  </select>
                )}
              </div>
              <Button
                onClick={sendMessage}
                disabled={loading || !input.trim()}
                size="sm"
                className="h-8 w-8 p-0 rounded-lg"
              >
                {loading
                  ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  : <Send className="w-3.5 h-3.5" />
                }
              </Button>
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-2 px-1">
            Agent responses are generated by the configured LLM and may not be perfectly accurate.
          </p>
        </div>
      </div>
    </div>
  )
}
