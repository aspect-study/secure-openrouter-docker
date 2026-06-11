import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { ChatMessage } from '@/components/ui/chat-message'
import { isDarkMode } from '@/lib/theme'

interface ModelResponseCardProps {
  name: string
  content: string
  latencyMs: number
}

export function ModelResponseCard({ name, content, latencyMs }: ModelResponseCardProps) {
  const dark = isDarkMode()

  return (
    <Card className="flex flex-col">
      <CardHeader className="flex-row items-center justify-between gap-2 pb-3 pt-4 px-4 shrink-0 border-b border-border">
        <span className="text-sm font-semibold truncate">{name}</span>
        <Badge variant="secondary" className="text-xs shrink-0 font-mono">
          {latencyMs < 1000 ? `${latencyMs}ms` : `${(latencyMs / 1000).toFixed(1)}s`}
        </Badge>
      </CardHeader>
      <CardContent className="p-4 text-sm">
        <ChatMessage content={content} isDark={dark} />
      </CardContent>
    </Card>
  )
}
