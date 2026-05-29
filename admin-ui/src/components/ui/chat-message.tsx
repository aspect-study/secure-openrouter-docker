import ReactMarkdown from 'react-markdown'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { Copy, Check } from 'lucide-react'
import { useState } from 'react'

interface ChatMessageProps {
  content: string
  isDark?: boolean
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }
  return (
    <button
      onClick={copy}
      className="absolute top-2 right-2 p-1 rounded text-xs text-muted-foreground hover:text-foreground bg-muted/80 hover:bg-muted transition-colors"
    >
      {copied ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
    </button>
  )
}

export function ChatMessage({ content, isDark = false }: ChatMessageProps) {
  return (
    <ReactMarkdown
      components={{
        // ── Code blocks ──────────────────────────────────────
        code({ node, className, children, ...props }) {
          const match = /language-(\w+)/.exec(className || '')
          const codeString = String(children).replace(/\n$/, '')
          const isBlock = !!match

          if (isBlock) {
            return (
              <div className="relative my-3 rounded-xl overflow-hidden border border-border text-sm">
                {/* Language badge */}
                <div className="flex items-center justify-between px-4 py-1.5 bg-muted/60 border-b border-border">
                  <span className="text-[11px] font-mono text-muted-foreground uppercase tracking-wider">
                    {match[1]}
                  </span>
                  <CopyButton text={codeString} />
                </div>
                <SyntaxHighlighter
                  style={isDark ? oneDark : oneLight}
                  language={match[1]}
                  PreTag="div"
                  customStyle={{
                    margin: 0,
                    padding: '1rem',
                    background: 'transparent',
                    fontSize: '0.8125rem',
                    lineHeight: '1.6',
                  }}
                  codeTagProps={{ style: { fontFamily: '"Fira Code", "Cascadia Code", monospace' } }}
                >
                  {codeString}
                </SyntaxHighlighter>
              </div>
            )
          }

          // Inline code
          return (
            <code
              className="px-1.5 py-0.5 rounded-md bg-muted text-foreground font-mono text-[0.8em]"
              {...props}
            >
              {children}
            </code>
          )
        },

        // ── Prose elements ───────────────────────────────────
        p({ children }) {
          return <p className="mb-2 last:mb-0 leading-relaxed">{children}</p>
        },
        h1({ children }) {
          return <h1 className="text-lg font-bold mt-4 mb-2 first:mt-0">{children}</h1>
        },
        h2({ children }) {
          return <h2 className="text-base font-semibold mt-3 mb-1.5 first:mt-0">{children}</h2>
        },
        h3({ children }) {
          return <h3 className="text-sm font-semibold mt-3 mb-1 first:mt-0">{children}</h3>
        },
        ul({ children }) {
          return <ul className="list-disc list-outside pl-5 mb-2 space-y-1">{children}</ul>
        },
        ol({ children }) {
          return <ol className="list-decimal list-outside pl-5 mb-2 space-y-1">{children}</ol>
        },
        li({ children }) {
          return <li className="leading-relaxed">{children}</li>
        },
        blockquote({ children }) {
          return (
            <blockquote className="border-l-2 border-primary/50 pl-3 my-2 text-muted-foreground italic">
              {children}
            </blockquote>
          )
        },
        strong({ children }) {
          return <strong className="font-semibold text-foreground">{children}</strong>
        },
        a({ href, children }) {
          return (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline underline-offset-2 hover:opacity-80"
            >
              {children}
            </a>
          )
        },
        hr() {
          return <hr className="my-3 border-border" />
        },
        table({ children }) {
          return (
            <div className="overflow-x-auto my-3">
              <table className="w-full text-sm border-collapse border border-border rounded-lg overflow-hidden">
                {children}
              </table>
            </div>
          )
        },
        th({ children }) {
          return (
            <th className="px-3 py-2 bg-muted font-semibold text-left border border-border text-xs">
              {children}
            </th>
          )
        },
        td({ children }) {
          return <td className="px-3 py-2 border border-border">{children}</td>
        },
      }}
    >
      {content}
    </ReactMarkdown>
  )
}
