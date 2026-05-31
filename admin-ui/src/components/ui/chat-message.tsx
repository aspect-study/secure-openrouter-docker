import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { Copy, Check } from 'lucide-react'
import { useState } from 'react'

interface ChatMessageProps {
  content: string
  isDark?: boolean
}

/**
 * Normalizes model-generated markdown tables so remark-gfm can parse them
 * reliably regardless of how much whitespace the model includes.
 *
 * Problems this fixes:
 *   - No spaces around pipe chars: |Day|Activity| -> | Day | Activity |
 *   - Separator row merged onto header line without newline
 *   - Missing blank line before the table (some parsers require it)
 */
function normalizeMarkdown(content: string): string {
  // Split on actual newlines; models sometimes emit \r\n
  const lines = content.split(/\r?\n/)
  const out: string[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()

    // Detect pipe-table rows (starts and/or ends with |)
    if (trimmed.startsWith('|') || (trimmed.includes('|') && /^\|?[\s\-|:]+\|?$/.test(trimmed))) {
      // Split potentially merged header+separator on the same line
      // e.g. "|A|B|C|---|---|---|" -> two lines
      const parts = trimmed.split(/(?<=\|)(?=\|?[-:]+[-|: ]*\|)/)
      for (const part of parts) {
        const cells = part.split('|')
        // Normalise each cell: trim whitespace, keep empty boundary cells
        const normalised = cells.map((c, idx) => {
          if (idx === 0 && c.trim() === '') return ''
          if (idx === cells.length - 1 && c.trim() === '') return ''
          // Separator cell: keep as-is but normalise dashes
          if (/^[\s\-:]+$/.test(c)) return ' ' + c.trim() + ' '
          return ' ' + c.trim() + ' '
        })
        out.push('|' + normalised.slice(1, -1).join('|') + '|')
      }
    } else {
      out.push(line)
    }
  }

  // Ensure a blank line precedes any table block so remark-gfm sees it
  const result: string[] = []
  for (let i = 0; i < out.length; i++) {
    const line = out[i]
    const prev = result[result.length - 1]
    const isTableRow = line.trim().startsWith('|')
    const prevIsTableRow = prev !== undefined && prev.trim().startsWith('|')
    const prevIsBlank = prev === '' || prev === undefined

    if (isTableRow && !prevIsTableRow && !prevIsBlank) {
      result.push('')
    }
    result.push(line)
  }

  return result.join('\n')
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
      remarkPlugins={[remarkGfm]}
      components={{
        code({ node, className, children, ...props }) {
          const match = /language-(\w+)/.exec(className || '')
          const codeString = String(children).replace(/\n$/, '')
          const isBlock = !!match

          if (isBlock) {
            return (
              <div className="relative my-3 rounded-xl overflow-hidden border border-border text-sm">
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

          return (
            <code
              className="px-1.5 py-0.5 rounded-md bg-muted text-foreground font-mono text-[0.8em]"
              {...props}
            >
              {children}
            </code>
          )
        },
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
        thead({ children }) {
          return <thead className="bg-muted/60">{children}</thead>
        },
        tbody({ children }) {
          return <tbody className="divide-y divide-border">{children}</tbody>
        },
        tr({ children }) {
          return <tr className="hover:bg-muted/30 transition-colors">{children}</tr>
        },
        th({ children }) {
          return (
            <th className="px-3 py-2 font-semibold text-left border border-border text-xs uppercase tracking-wide">
              {children}
            </th>
          )
        },
        td({ children }) {
          return <td className="px-3 py-2 border border-border text-sm">{children}</td>
        },
      }}
    >
      {normalizeMarkdown(content)}
    </ReactMarkdown>
  )
}
