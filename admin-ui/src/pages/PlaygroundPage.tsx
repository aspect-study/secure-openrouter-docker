import React, { useEffect, useState, useRef, useCallback } from 'react'
import { chatApi } from '@/lib/api'
import { useAuth } from '@/hooks/useAuth'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Command, CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator } from '@/components/ui/command'
import { toast } from 'sonner'
import { cn, modelEmoji, modelDisplayName, modelCapability, modelDescription, formatTime } from '@/lib/utils'
import { applyTheme, isDarkMode } from '@/lib/theme'
import { ChatMessage } from '@/components/ui/chat-message'
import { useIsMobile } from '@/hooks/useWindowSize'
import { ChangePasswordDialog } from '@/components/ui/change-password-dialog'
import {
  Send, Plus, Trash2, Menu, X, Copy, KeyRound,
  Moon, Sun, LogOut, Settings, ChevronRight, Zap
} from 'lucide-react'

interface Model { id: string }
interface Message { id?: number; role: 'user' | 'assistant'; content: string; createdAt?: string }
interface Conversation { id: number; title: string; model: string; updatedAt: string }
interface TokenUsage { promptTokens: number; completionTokens: number; totalTokens: number }


export default function PlaygroundPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [models, setModels] = useState<Model[]>([])
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [activeConversation, setActiveConversation] = useState<Conversation | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [selectedModel, setSelectedModel] = useState<string>('')
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [lastUsage, setLastUsage] = useState<TokenUsage | null>(null)
  const [changePwOpen, setChangePwOpen] = useState(false)
  const isMobile = useIsMobile()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [commandOpen, setCommandOpen] = useState(false)
  const [dark, setDark] = useState(isDarkMode)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Load models and conversations
  useEffect(() => {
    chatApi.getModels().then(r => {
      const list: Model[] = r.data.models.map((id: string) => ({ id }))
      setModels(list)
      if (list.length > 0) setSelectedModel(list[0].id)
    })
    chatApi.getConversations().then(r => setConversations(r.data))
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

  const sendMessage = async () => {
    if (!input.trim() || loading) return

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

    const userMessage: Message = { role: 'user', content: input, createdAt: new Date().toISOString() }
    setMessages(prev => [...prev, userMessage])
    setInput('')
    setLoading(true)

    try {
      const r = await chatApi.sendMessage(convId, userMessage.content)
      const { message, title, usage } = r.data
      if (usage) setLastUsage(usage)
      setMessages(prev => [...prev, message])
      if (title) {
        setActiveConversation(prev => prev ? { ...prev, title } : prev)
        setConversations(prev => prev.map(c => c.id === convId ? { ...c, title } : c))
      }
    } catch (err: unknown) {
      const errData = (err as { response?: { status?: number; data?: { error?: string; statusCode?: number } } })?.response
      const status = errData?.status ?? 0
      const msg = errData?.data?.error ?? 'Failed to send message'

      if (status === 429) {
        toast.error(`Rate limited: ${msg}`, {
          description: 'Wait a few seconds, then try again or switch to a different model.',
          duration: 6000,
        })
      } else if (status >= 500) {
        toast.error('Model temporarily unavailable', {
          description: 'Try switching to a different model via Ctrl+K.',
          duration: 5000,
        })
      } else {
        toast.error(msg)
      }
      // Remove the optimistically added user message
      setMessages(prev => prev.slice(0, -1))
      // Restore the input so the user doesn't lose their message
      setInput(userMessage.content)
    } finally {
      setLoading(false)
      textareaRef.current?.focus()
    }
  }

  const copyMessage = (content: string) => {
    navigator.clipboard.writeText(content)
    toast.success('Copied to clipboard')
  }


  const suggestedPrompts = [
    'Explain how neural networks work in simple terms',
    'Write a Python function to sort a list of dictionaries',
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
          <Button variant="ghost" size="icon" className="h-7 w-7 flex-1" title="Change password"
            onClick={() => setChangePwOpen(true)}>
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
                        : <ChatMessage content={msg.content} isDark={dark} />
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

              {/* Typing indicator */}
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
                  if (e.key === 'Enter' && !e.shiftKey) {
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
                  disabled={loading || !input.trim()}
                  size="sm"
                  className="h-8 w-8 p-0 rounded-lg"
                >
                  <Send className="w-3.5 h-3.5" />
                </Button>
              </div>
            </div>
            <p className="text-xs text-muted-foreground text-center mt-2">
              AI can make mistakes. Verify important information.
            </p>
          </div>
        </div>
      </div>

      {/* Command palette — uses CommandDialog for proper focus trap + keyboard nav */}
      <ChangePasswordDialog open={changePwOpen} onOpenChange={setChangePwOpen} />

      <CommandDialog open={commandOpen} onOpenChange={setCommandOpen}>
        <Command>
          {/* Active model indicator */}
          <div className="flex items-center gap-2 px-4 py-2.5 border-b border-border/50">
            <span className="text-base">{modelEmoji(selectedModel)}</span>
            <span className="text-xs text-muted-foreground">Active:</span>
            <span className="text-xs font-medium truncate">{modelDisplayName(selectedModel)}</span>
          </div>

          <CommandInput placeholder="Search free models..." />

          <CommandList className="max-h-[220px]">
          <CommandEmpty>No models found.</CommandEmpty>

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
            const group = models.filter(m =>
              prefix ? m.id.startsWith(prefix) : !excluded.some(p => m.id.startsWith(p))
            )
            if (group.length === 0) return null
            return (
              <React.Fragment key={label}>
              <CommandSeparator />
              <CommandGroup heading={`${emoji} ${label}`}>
                {group.map(model => {
                  const isActive = selectedModel === model.id
                  return (
                    <CommandItem
                      key={model.id}
                      value={`${modelDisplayName(model.id)} ${model.id}`}
                      onSelect={async () => {
                        const newModel = model.id
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
                        'data-[selected=true]:bg-accent data-[selected=true]:border-border data-[selected=true]:shadow-sm',
                        isActive && 'bg-primary/8'
                      )}
                    >
                      <div className="flex-1 min-w-0">
                        {/* Row 1: name + badges */}
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <p className="text-xs font-medium truncate">{modelDisplayName(model.id)}</p>
                          {isActive && (
                            <span className="text-[10px] font-semibold text-primary bg-primary/15 px-1.5 py-0.5 rounded-full shrink-0">
                              Active
                            </span>
                          )}
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-secondary text-secondary-foreground shrink-0">
                            {modelCapability(model.id)}
                          </span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-muted text-muted-foreground shrink-0">
                            {modelDescription(model.id).context}
                          </span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-muted text-muted-foreground shrink-0">
                            {modelDescription(model.id).rpm}
                          </span>
                        </div>
                        {/* Row 2: best use */}
                        <div className="flex items-center gap-1 mt-0.5">
                          <span className="text-[10px] text-emerald-500 shrink-0">✓</span>
                          <p className="text-[10px] text-muted-foreground truncate">{modelDescription(model.id).use}</p>
                        </div>
                        {/* Row 3: limitation */}
                        <div className="flex items-center gap-1">
                          <span className="text-[10px] text-amber-500 shrink-0">⚠</span>
                          <p className="text-[10px] text-muted-foreground/60 truncate">{modelDescription(model.id).limit}</p>
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
            <p className="text-xs text-muted-foreground">{models.length} free models</p>
          </div>
        </Command>
      </CommandDialog>
    </div>
  )
}
