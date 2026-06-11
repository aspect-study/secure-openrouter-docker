import React, { useEffect, useState, useRef } from 'react'
import { chatApi, apiKeyApi } from '@/lib/api'
import { useEffectiveModels } from '@/hooks/useEffectiveModels'
import { useAuth } from '@/hooks/useAuth'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Command, CommandDialog, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator } from '@/components/ui/command'
import { toast } from 'sonner'
import { cn, modelEmoji, modelDisplayName, modelCapability, modelDescription, formatTime } from '@/lib/utils'
import { applyTheme, isDarkMode } from '@/lib/theme'
import { ChatMessage } from '@/components/ui/chat-message'
import { useIsMobile } from '@/hooks/useWindowSize'
import { ChangePasswordDialog } from '@/components/ui/change-password-dialog'
import {
  Send, Plus, Trash2, Menu, X, Copy, KeyRound,
  Moon, Sun, LogOut, Settings, ChevronRight, Zap, AlertTriangle, Sliders
} from 'lucide-react'
interface Message { id?: number; role: 'user' | 'assistant'; content: string; createdAt?: string; streaming?: boolean }
interface Conversation { id: number; title: string; model: string; updatedAt: string }
interface TokenUsage { promptTokens: number; completionTokens: number; totalTokens: number }

// Sentinel ID used to identify the in-progress streaming assistant bubble
const STREAMING_MSG_ID = -999


export default function PlaygroundPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  // Model list — user-scoped (PRD-003): uses GET /api/user/models instead of GET /api/chat/models
  const { models: allUserModels, totalUserEnabled } = useEffectiveModels()
  // Playground only shows models the user has effectively enabled
  const models = allUserModels.filter(m => m.effectivelyEnabled)
  const noModelsEnabled = totalUserEnabled === 0

  const [conversations, setConversations] = useState<Conversation[]>([])
  const [activeConversation, setActiveConversation] = useState<Conversation | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [selectedModel, setSelectedModel] = useState<string>('')
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [lastUsage, setLastUsage] = useState<TokenUsage | null>(null)
  const [changePwOpen, setChangePwOpen] = useState(false)
  const [keyConfigured, setKeyConfigured] = useState<boolean | null>(null)
  const isMobile = useIsMobile()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [commandOpen, setCommandOpen] = useState(false)
  const [modelSearch, setModelSearch] = useState('')
  const [dark, setDark] = useState(isDarkMode)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // When the effective model list loads, pick a default model if none is selected
  useEffect(() => {
    if (models.length > 0 && !selectedModel) {
      const DEFAULT_MODEL = 'nvidia/nemotron-nano-9b-v2:free'
      const preferred = models.find(m => m.modelId === DEFAULT_MODEL) ?? models[0]
      setSelectedModel(preferred.modelId)
    }
  }, [models, selectedModel])

  // Load conversations and key status on mount
  useEffect(() => {
    chatApi.getConversations().then(r => setConversations(r.data))
    apiKeyApi.getStatus().then(r => setKeyConfigured(r.data.configured)).catch(() => setKeyConfigured(false))
  }, [])


  // Keyboard shortcut for command palette
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault()
        setCommandOpen(true)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  // Auto-scroll
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])


  const toggleTheme = () => {
    const next = !dark
    setDark(next)
    applyTheme(next)
  }

  const startNewConversation = async () => {
    if (!selectedModel) return
    try {
      const r = await chatApi.createConversation(selectedModel)
      const conv: Conversation = r.data
      setConversations(prev => [conv, ...prev])
      setActiveConversation(conv)
      setMessages([])
    } catch {
      toast.error('Failed to create conversation')
    }
  }

  const openConversation = async (conv: Conversation) => {
    setActiveConversation(conv)
    setSelectedModel(conv.model)
    setLastUsage(null)
    try {
      const r = await chatApi.getConversation(conv.id)
      setMessages(r.data.messages)
    } catch {
      toast.error('Failed to load conversation')
    }
  }

  const deleteConversation = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      await chatApi.deleteConversation(id)
      setConversations(prev => prev.filter(c => c.id !== id))
      if (activeConversation?.id === id) {
        setActiveConversation(null)
        setMessages([])
      }
    } catch {
      toast.error('Failed to delete conversation')
    }
  }

  /**
   * Primary send path — uses SSE streaming via fetch + ReadableStream.
   *
   * Why fetch instead of EventSource: EventSource only supports GET.
   * We need POST with a JSON body and an Authorization header (JWT).
   *
   * Flow:
   *   1. Ensure conversation exists (create if needed)
   *   2. Optimistically render user message
   *   3. Add empty streaming assistant bubble (id=STREAMING_MSG_ID, streaming=true)
   *   4. Open fetch stream to /messages/stream
   *   5. Parse SSE lines manually: split on '\n\n', look for event/data pairs
   *   6. On 'token': append to the streaming bubble via functional state update
   *   7. On 'done': replace streaming bubble with the real persisted message + update title/usage
   *   8. On error/disconnect: remove streaming bubble, restore input, show toast
   */
  const sendMessage = async () => {
    if (!input.trim() || loading || streaming) return

    let convId = activeConversation?.id
    if (!convId) {
      try {
        const r = await chatApi.createConversation(selectedModel)
        const conv: Conversation = r.data
        setConversations(prev => [conv, ...prev])
        setActiveConversation(conv)
        convId = conv.id
      } catch {
        toast.error('Failed to create conversation')
        return
      }
    }

    const userContent = input
    const userMessage: Message = { role: 'user', content: userContent, createdAt: new Date().toISOString() }
    // Streaming placeholder — replaced by real message on 'done'
    const streamingBubble: Message = {
      id: STREAMING_MSG_ID,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      streaming: true,
    }

    setMessages(prev => [...prev, userMessage, streamingBubble])
    setInput('')
    setStreaming(true)
    setLoading(false)

    const token = localStorage.getItem('token') ?? sessionStorage.getItem('token') ?? ''

    try {
      const response = await fetch(`/api/conversations/${convId}/messages/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({ content: userContent }),
      })

      if (!response.ok || !response.body) {
        const errBody = await response.text().catch(() => '')
        let errMsg = 'Failed to send message'
        try { errMsg = JSON.parse(errBody)?.error ?? errMsg } catch { /* raw text */ }

        if (response.status === 429) {
          // Parse resetAt from usage limit response if present
          let desc = 'Wait a few seconds, then try again or switch to a different model.'
          try {
            const body = JSON.parse(await response.text())
            if (body.resetAt) {
              const ms = new Date(body.resetAt).getTime() - Date.now()
              const h = Math.floor(ms / 3600000)
              const m = Math.floor((ms % 3600000) / 60000)
              desc = `Daily limit reached. Resets in ${h}h ${m}m.`
            }
          } catch { /* ignore */ }
          toast.error(errMsg, { description: desc, duration: 8000 })
        } else if (response.status === 409) {
          // KeyNotConfiguredException — no key set
          toast.error('No API key configured', {
            description: 'Add your OpenRouter API key in Settings to start chatting.',
            action: { label: 'Go to Settings', onClick: () => navigate('/settings') },
            duration: 10000,
          })
          setKeyConfigured(false)
        } else if (response.status === 404) {
          toast.error('Conversation not found')
        } else {
          toast.error(errMsg)
        }
        // Remove both optimistic messages, restore input
        setMessages(prev => prev.slice(0, -2))
        setInput(userContent)
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      // SSE parsing state — we may receive partial chunks from the network
      let currentEvent = ''
      let currentData = ''

      const processLine = (line: string) => {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          // SSE spec: one optional space after the colon — strip only that leading space,
          // not all whitespace (trimming would corrupt JSON strings containing \n)
          currentData = line.slice(5).replace(/^ /, '')
        } else if (line === '') {
          // Blank line = end of event block
          // Note: currentData may legitimately be empty string (e.g. JSON "")
          // so check for currentEvent presence, not currentData truthiness
          if (currentEvent) {
            handleSseEvent(currentEvent, currentData, convId!)
          }
          currentEvent = ''
          currentData = ''
        }
      }

      const handleSseEvent = (event: string, data: string, cid: number) => {
        if (event === 'token') {
          // Token data is JSON-encoded so whitespace chars (\n, \t) survive SSE transport.
          // Raw \n in SSE data: fields is treated as an empty line by the protocol,
          // silently dropping newlines and collapsing tables/code onto one line.
          let token = data
          try { token = JSON.parse(data) } catch { /* fallback to raw data */ }
          setMessages(prev => prev.map(m =>
            m.id === STREAMING_MSG_ID
              ? { ...m, content: m.content + token }
              : m
          ))
        } else if (event === 'done') {
          try {
            const payload = JSON.parse(data)
            // Replace streaming bubble with the backend-normalized content.
            // Using normalizedContent (from MarkdownNormalizer) ensures the bubble
            // matches exactly what was persisted — tables, spacing, separator rows fixed.
            const finalContent = payload.normalizedContent ?? payload.content ?? ''
            const realMessage: Message = {
              id: payload.messageId,
              role: 'assistant',
              content: finalContent,
              createdAt: new Date().toISOString(),
              streaming: false,
            }
            setMessages(prev => prev.map(m =>
              m.id === STREAMING_MSG_ID ? realMessage : m
            ))
            if (payload.title) {
              setActiveConversation(prev => prev ? { ...prev, title: payload.title } : prev)
              setConversations(prev => prev.map(c =>
                c.id === cid ? { ...c, title: payload.title } : c
              ))
            }
            if (payload.usage) setLastUsage(payload.usage)
          } catch {
            // Malformed done payload — bubble stays with streamed content, that's fine
          }
        } else if (event === 'error') {
          try {
            const payload = JSON.parse(data)
            toast.error(payload.error ?? 'Stream error')
          } catch {
            toast.error('Stream error')
          }
        }
      }

      // Read stream chunks until done
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // Process complete lines
        const lines = buffer.split('\n')
        // Keep last (potentially incomplete) line in buffer
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          processLine(line)
        }
      }

      // Process any remaining buffered content
      if (buffer) processLine(buffer)

    } catch (err) {
      // Network error or stream abort
      toast.error('Connection lost. Please try again.')
      setMessages(prev => prev.filter(m => m.id !== STREAMING_MSG_ID).slice(0, -1))
      setInput(userContent)
    } finally {
      setStreaming(false)
      textareaRef.current?.focus()
    }
  }

  const copyMessage = (content: string) => {
    navigator.clipboard.writeText(content)
    toast.success('Copied to clipboard')
  }


  const suggestedPrompts = [
    'Explain how neural networks work in simple terms',
    'Write a Java method to sort a list of maps by a specific key',
    'What are the key differences between REST and GraphQL?',
    'Help me debug this code...',
  ]

  // Sidebar content — shared between desktop and mobile overlay
  const SidebarContent = () => (
    <>
      {/* Header */}
      <div className="p-4 border-b border-border flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <Zap className="w-4 h-4 text-primary" />
          <span className="font-semibold text-sm">Conversations</span>
        </div>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" className="h-7 w-7" onClick={startNewConversation}>
            <Plus className="w-4 h-4" />
          </Button>
          {isMobile && (
            <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => setSidebarOpen(false)}>
              <X className="w-4 h-4" />
            </Button>
          )}
        </div>
      </div>

      {/* Conversations list */}
      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {conversations.length === 0 ? (
          <p className="text-xs text-muted-foreground text-center py-8">
            No conversations yet.<br />Start one below!
          </p>
        ) : conversations.map(conv => (
          <div
            key={conv.id}
            onClick={() => { openConversation(conv); if (isMobile) setSidebarOpen(false) }}
            className={cn(
              'group flex items-center justify-between px-3 py-2 rounded-lg cursor-pointer text-sm transition-colors',
              activeConversation?.id === conv.id
                ? 'bg-primary text-primary-foreground'
                : 'hover:bg-accent text-foreground'
            )}
          >
            <div className="flex-1 min-w-0">
              <p className="truncate font-medium text-xs">{conv.title}</p>
              <p className={cn(
                'text-xs truncate',
                activeConversation?.id === conv.id ? 'text-primary-foreground/70' : 'text-muted-foreground'
              )}>
                {modelDisplayName(conv.model)}
              </p>
            </div>
            <Button
              variant="ghost" size="icon"
              className="h-6 w-6 opacity-0 group-hover:opacity-100 shrink-0"
              onClick={(e) => deleteConversation(conv.id, e)}
            >
              <Trash2 className="w-3 h-3" />
            </Button>
          </div>
        ))}
      </div>

      {/* Settings nav links */}
      <div className="px-2 pb-1 shrink-0 space-y-0.5">
        <button
          onClick={() => navigate('/settings')}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
        >
          <KeyRound className="w-3.5 h-3.5 shrink-0" />
          <span>Settings &amp; API Key</span>
          {keyConfigured === false && (
            <span className="ml-auto w-2 h-2 rounded-full bg-yellow-500 shrink-0" title="API key not configured" />
          )}
        </button>
        <button
          onClick={() => navigate('/settings?tab=models')}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
        >
          <Sliders className="w-3.5 h-3.5 shrink-0" />
          <span>My Models</span>
          {noModelsEnabled && (
            <span className="ml-auto w-2 h-2 rounded-full bg-yellow-500 shrink-0" title="All models disabled" />
          )}
        </button>
      </div>

      {/* User area */}
      <div className="p-3 border-t border-border space-y-2 shrink-0">
        <p className="text-xs text-muted-foreground truncate px-1">{user?.email}</p>
        <div className="flex gap-1">
          <Button variant="ghost" size="icon" className="h-7 w-7 flex-1" onClick={toggleTheme}>
            {dark ? <Sun className="w-3 h-3" /> : <Moon className="w-3 h-3" />}
          </Button>
          {user?.role === 'ADMIN' && (
            <Button variant="ghost" size="icon" className="h-7 w-7 flex-1"
              onClick={() => navigate('/admin/dashboard')}>
              <Settings className="w-3 h-3" />
            </Button>
          )}
          <Button variant="ghost" size="icon" className="h-7 w-7 flex-1" title="API Key Settings"
            onClick={() => navigate('/settings')}>
            <KeyRound className="w-3 h-3" />
          </Button>
          <Button variant="ghost" size="icon" className="h-7 w-7 flex-1" title="Sign out"
            onClick={() => { logout(); navigate('/login') }}>
            <LogOut className="w-3 h-3" />
          </Button>
        </div>
      </div>
    </>
  )

  return (
    <div className="flex h-screen bg-background overflow-hidden">
      {/* Desktop sidebar — fixed panel */}
      {!isMobile && sidebarOpen && (
        <aside className="w-72 border-r border-border flex flex-col shrink-0">
          <SidebarContent />
        </aside>
      )}

      {/* Mobile sidebar — full overlay drawer */}
      {isMobile && sidebarOpen && (
        <div className="fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"
            onClick={() => setSidebarOpen(false)} />
          <aside className="absolute left-0 top-0 h-full w-72 bg-background border-r border-border flex flex-col shadow-2xl">
            <SidebarContent />
          </aside>
        </div>
      )}

      {/* Main */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <header className="border-b border-border px-4 py-3 flex items-center gap-3 shrink-0 backdrop-blur-sm bg-background/80 sticky top-0 z-10">
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setSidebarOpen(!sidebarOpen)}>
            {sidebarOpen ? <X className="w-4 h-4" /> : <Menu className="w-4 h-4" />}
          </Button>

          <div className="flex items-center gap-2 flex-1 min-w-0">
            <span className="text-lg">{modelEmoji(selectedModel)}</span>
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{modelDisplayName(selectedModel)}</p>
              {lastUsage ? (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <span className="text-foreground font-medium">{lastUsage.totalTokens.toLocaleString()}</span>
                  <span>tokens last response</span>
                  <span className="text-muted-foreground/50">·</span>
                  <span>{lastUsage.promptTokens.toLocaleString()} in</span>
                  <span className="text-muted-foreground/50">·</span>
                  <span>{lastUsage.completionTokens.toLocaleString()} out</span>
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">
                  {messages.length > 0 ? 'Send a message to see token usage' : 'No messages yet'}
                </p>
              )}
            </div>
          </div>

          <Button variant="outline" size="sm" className="text-xs gap-1 shrink-0"
            onClick={() => setCommandOpen(true)}>
            <span>Switch Model</span>
            <kbd className="text-[10px] bg-muted px-1 rounded">⌘K</kbd>
          </Button>
        </header>

        {/* All-models-disabled banner — user has disabled every model in My Models */}
        {noModelsEnabled && (
          <div className="mx-4 mt-3 flex items-center gap-3 px-4 py-3 rounded-xl border border-yellow-500/30 bg-yellow-500/10 text-sm">
            <AlertTriangle className="w-4 h-4 text-yellow-500 shrink-0" />
            <span className="text-foreground flex-1">
              All models are disabled. Go to{' '}
              <button
                onClick={() => navigate('/settings')}
                className="font-medium text-yellow-600 dark:text-yellow-400 hover:underline"
              >
                Settings → My Models
              </button>
              {' '}to enable at least one.
            </span>
          </div>
        )}

        {/* No-key banner */}
        {keyConfigured === false && (
          <div className="mx-4 mt-3 flex items-center gap-3 px-4 py-3 rounded-xl border border-yellow-500/30 bg-yellow-500/10 text-sm">
            <AlertTriangle className="w-4 h-4 text-yellow-500 shrink-0" />
            <span className="text-foreground flex-1">
              Add your OpenRouter API key in Settings to start chatting.
            </span>
            <button
              onClick={() => navigate('/settings')}
              className="text-xs font-medium text-yellow-600 dark:text-yellow-400 hover:underline shrink-0"
            >
              Go to Settings →
            </button>
          </div>
        )}

        {/* Messages */}
        <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
          {messages.length === 0 && !loading ? (
            // Empty state
            <div className="flex flex-col items-center justify-center h-full gap-8 text-center px-4">
              <div className="space-y-2">
                <span className="text-5xl block">{modelEmoji(selectedModel)}</span>
                <h2 className="text-2xl font-semibold tracking-tight mt-4">
                  How can I assist you today?
                </h2>
                <p className="text-sm text-muted-foreground">
                  Using <span className="font-medium text-foreground">{modelDisplayName(selectedModel)}</span>
                  &nbsp;·&nbsp;<Badge variant="secondary" className="text-xs">{modelCapability(selectedModel)}</Badge>
                </p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full max-w-lg">
                {suggestedPrompts.map(prompt => (
                  <button key={prompt}
                    className="text-left px-4 py-3 rounded-xl border border-border bg-card hover:bg-accent hover:border-primary/30 transition-all text-sm text-muted-foreground hover:text-foreground shadow-sm"
                    onClick={() => { setInput(prompt); textareaRef.current?.focus() }}>
                    {prompt}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <>
              {messages.map((msg, i) => (
                <div key={i} className={cn('message-wrapper flex', msg.role === 'user' ? 'justify-end' : 'justify-start')}>
                  {msg.role === 'assistant' && (
                    <span className="text-2xl mr-2 mt-1 shrink-0">{modelEmoji(selectedModel)}</span>
                  )}
                  <div className={cn(msg.role === 'user' ? 'max-w-[72%]' : 'max-w-[85%]', 'space-y-1')}>
                    <div className={cn(
                      'rounded-2xl px-4 py-3 text-sm shadow-sm',
                      msg.role === 'user'
                        ? 'rounded-br-sm text-primary-foreground'
                        : 'rounded-bl-sm bg-card border border-border text-foreground'
                    )}
                    style={msg.role === 'user' ? {
                      background: 'linear-gradient(135deg, var(--primary) 0%, oklch(from var(--primary) calc(l - 0.05) calc(c + 0.02) calc(h - 5)) 100%)',
                      boxShadow: '0 2px 12px oklch(from var(--primary) l c h / 25%)'
                    } : {}}>
                      {msg.role === 'user'
                        ? <p className="leading-relaxed whitespace-pre-wrap">{msg.content}</p>
                        : msg.streaming && msg.content === ''
                          /* First-token wait: show typing dots inside the bubble */
                          ? <div className="flex items-center gap-1.5 py-0.5">
                              <div className="typing-dot" />
                              <div className="typing-dot" />
                              <div className="typing-dot" />
                            </div>
                          : <span>
                              <ChatMessage content={msg.content} isDark={dark} isStreaming={msg.streaming} />
                              {/* Blinking cursor while tokens are arriving */}
                              {msg.streaming && (
                                <span className="inline-block w-0.5 h-4 bg-current ml-0.5 align-middle animate-pulse" />
                              )}
                            </span>
                      }
                    </div>
                    <div className={cn('flex items-center gap-1', msg.role === 'user' ? 'justify-end' : 'justify-start')}>
                      {msg.createdAt && (
                        <span className="text-xs text-muted-foreground">{formatTime(msg.createdAt)}</span>
                      )}
                      {msg.role === 'assistant' && (
                        <div className="message-actions flex gap-1">
                          <Button variant="ghost" size="icon" className="h-5 w-5"
                            onClick={() => copyMessage(msg.content)}>
                            <Copy className="w-3 h-3" />
                          </Button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}

              {/* Typing indicator — shown only while waiting for the first token */}
              {loading && (
                <div className="flex items-start gap-2">
                  <span className="text-2xl">{modelEmoji(selectedModel)}</span>
                  <div className="bg-muted rounded-2xl rounded-bl-sm px-5 py-4 flex items-center gap-1.5 border border-border/50">
                    <div className="typing-dot" />
                    <div className="typing-dot" />
                    <div className="typing-dot" />
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </>
          )}
        </div>

        {/* Input — Claude-style: full-width card with send inside */}
        <div className="border-t border-border px-4 py-4 shrink-0">
          <div className="max-w-4xl mx-auto">
            <div className="relative flex flex-col rounded-2xl border border-border bg-card transition-shadow focus-within:shadow-[0_0_0_2px_var(--ring),0_4px_16px_oklch(from_var(--primary)_l_c_h_/_12%)]" style={{boxShadow: '0 2px 8px oklch(0 0 0 / 6%)'}}>
              <Textarea
                ref={textareaRef}
                placeholder="Message the AI... (Enter to send · Shift+Enter for new line)"
                value={input}
                onChange={e => {
                  setInput(e.target.value)
                  // Auto-resize
                  e.target.style.height = 'auto'
                  e.target.style.height = Math.min(e.target.scrollHeight, 240) + 'px'
                }}
                onKeyDown={e => {
                  if (e.key === 'Enter' && !e.shiftKey && !streaming) {
                    e.preventDefault()
                    sendMessage()
                  }
                }}
                className="min-h-[80px] max-h-60 resize-none border-0 shadow-none bg-transparent px-4 pt-3 pb-12 text-sm leading-relaxed focus-visible:ring-0 focus-visible:outline-none"
                rows={3}
              />
              {/* Footer bar inside the card */}
              <div className="absolute bottom-0 left-0 right-0 flex items-center justify-between px-3 py-2 border-t border-border/50">
                <div className="flex items-center gap-2">
                  {/* Clickable model name — opens command palette */}
                  <button
                    onClick={() => setCommandOpen(true)}
                    className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors group"
                  >
                    <span>{modelEmoji(selectedModel)}</span>
                    <span className="truncate max-w-[120px] group-hover:underline underline-offset-2">
                      {modelDisplayName(selectedModel)}
                    </span>
                    <ChevronRight className="w-3 h-3 opacity-50 group-hover:opacity-100 transition-opacity" />
                  </button>
                  {input.length > 0 && (
                    <span className="text-xs text-muted-foreground/50">{input.length}</span>
                  )}
                </div>
                <Button
                  onClick={sendMessage}
                  disabled={loading || streaming || !input.trim() || noModelsEnabled}
                  size="sm"
                  className="h-8 w-8 p-0 rounded-lg"
                >
                  <Send className="w-3.5 h-3.5" />
                </Button>
              </div>
            </div>
            <div className="flex items-center justify-between mt-2 px-1">
              <p className="text-xs text-muted-foreground">
                AI can make mistakes. Verify important information.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Command palette — uses CommandDialog for proper focus trap + keyboard nav */}
      <ChangePasswordDialog open={changePwOpen} onOpenChange={setChangePwOpen} />

      <CommandDialog open={commandOpen} onOpenChange={v => { setCommandOpen(v); if (!v) setModelSearch('') }}>
        <Command shouldFilter={false}>
          {/* Active model indicator */}
          <div className="flex items-center gap-2 px-4 py-2.5 border-b border-border/50">
            <span className="text-base">{modelEmoji(selectedModel)}</span>
            <span className="text-xs text-muted-foreground">Active:</span>
            <span className="text-xs font-medium truncate">{modelDisplayName(selectedModel)}</span>
          </div>

          <CommandInput
            placeholder="Search free models..."
            value={modelSearch}
            onValueChange={setModelSearch}
          />

          <CommandList className="max-h-[220px]">
          {modelSearch && !models.some(m =>
            m.modelId.toLowerCase().includes(modelSearch.toLowerCase()) ||
            modelDisplayName(m.modelId).toLowerCase().includes(modelSearch.toLowerCase())
          ) && (
            <p className="py-6 text-center text-sm text-muted-foreground">No models found.</p>
          )}

          {[
            { label: 'NVIDIA', emoji: '🧠', prefix: 'nvidia/' },
            { label: 'Meta', emoji: '🦙', prefix: 'meta-llama/' },
            { label: 'Google', emoji: '💎', prefix: 'google/' },
            { label: 'OpenAI', emoji: '⚡', prefix: 'openai/' },
            { label: 'DeepSeek', emoji: '🌊', prefix: 'deepseek/' },
            { label: 'Qwen', emoji: '🔮', prefix: 'qwen/' },
            { label: 'Moonshot', emoji: '🌙', prefix: 'moonshotai/' },
            { label: 'Liquid AI', emoji: '💧', prefix: 'liquid/' },
            { label: 'Poolside', emoji: '🏊', prefix: 'poolside/' },
            { label: 'Others', emoji: '🤖', prefix: '' },
          ].map(({ label, emoji, prefix }) => {
            const excluded = ['nvidia/', 'meta-llama/', 'google/', 'openai/', 'deepseek/', 'qwen/', 'moonshotai/', 'liquid/', 'poolside/']
            const q = modelSearch.toLowerCase()
            const group = models.filter(m => {
              const inGroup = prefix ? m.modelId.startsWith(prefix) : !excluded.some(p => m.modelId.startsWith(p))
              if (!inGroup) return false
              if (!q) return true
              return m.modelId.toLowerCase().includes(q) || modelDisplayName(m.modelId).toLowerCase().includes(q)
            })
            if (group.length === 0) return null
            return (
              <React.Fragment key={label}>
              <CommandSeparator />
              <CommandGroup heading={`${emoji} ${label}`}>
                {group.map(model => {
                  const isActive = selectedModel === model.modelId
                  return (
                    <CommandItem
                      key={model.id}
                      value={`${modelDisplayName(model.modelId)} ${model.modelId}`}
                      onSelect={async () => {
                        const newModel = model.modelId
                        setSelectedModel(newModel)
                        setCommandOpen(false)

                        // If switching to a different model, start a fresh conversation
                        if (activeConversation && activeConversation.model !== newModel) {
                          try {
                            const r = await chatApi.createConversation(newModel)
                            const conv: Conversation = r.data
                            setConversations(prev => [conv, ...prev])
                            setActiveConversation(conv)
                            setMessages([])
                            setLastUsage(null)
                            toast.success(`New conversation with ${modelDisplayName(newModel)}`)
                          } catch {
                            toast.error('Failed to start new conversation')
                          }
                        } else {
                          toast.success(`Switched to ${modelDisplayName(newModel)}`)
                        }
                      }}
                      className={cn(
                        'cursor-pointer rounded-lg my-0.5 px-3 py-2',
                        'border border-transparent',
                        'hover:bg-accent active:bg-accent/80',
                        'data-[selected=true]:bg-accent data-[selected=true]:border-border data-[selected=true]:shadow-sm',
                        isActive && 'bg-primary/8'
                      )}
                    >
                      <div className="flex-1 min-w-0">
                        {/* Row 1: name + badges */}
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <p className="text-xs font-medium truncate">{modelDisplayName(model.modelId)}</p>
                          {isActive && (
                            <span className="text-[10px] font-semibold text-primary bg-primary/15 px-1.5 py-0.5 rounded-full shrink-0">
                              Active
                            </span>
                          )}
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-secondary text-secondary-foreground shrink-0">
                            {modelCapability(model.modelId)}
                          </span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-muted text-muted-foreground shrink-0">
                            {modelDescription(model.modelId).context}
                          </span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-muted text-muted-foreground shrink-0">
                            {modelDescription(model.modelId).rpm}
                          </span>
                        </div>
                        {/* Row 2: best use */}
                        <div className="flex items-center gap-1 mt-0.5">
                          <span className="text-[10px] text-emerald-500 shrink-0">✓</span>
                          <p className="text-[10px] text-muted-foreground truncate">{modelDescription(model.modelId).use}</p>
                        </div>
                        {/* Row 3: limitation */}
                        <div className="flex items-center gap-1">
                          <span className="text-[10px] text-amber-500 shrink-0">⚠</span>
                          <p className="text-[10px] text-muted-foreground/60 truncate">{modelDescription(model.modelId).limit}</p>
                        </div>
                      </div>
                    </CommandItem>
                  )
                })}
              </CommandGroup>
              </React.Fragment>
            )
          })}
        </CommandList>


          {/* Footer */}
          <div className="px-4 py-2 border-t border-border flex items-center justify-between">
            <p className="text-xs text-muted-foreground">
              <span className="font-mono bg-muted px-1 rounded">↑↓</span> navigate &nbsp;
              <span className="font-mono bg-muted px-1 rounded">↵</span> select &nbsp;
              <span className="font-mono bg-muted px-1 rounded">Esc</span> close
            </p>
            <p className="text-xs text-muted-foreground">{models.length} available models</p>
          </div>
        </Command>
      </CommandDialog>
    </div>
  )
}
