import { useEffect, useState } from 'react'
import { adminApi } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { MessageSquare, Zap, Users, Cpu } from 'lucide-react'
import {
  Chart as ChartJS, CategoryScale, LinearScale,
  PointElement, LineElement, Title, Tooltip, Legend, Filler
} from 'chart.js'
import { Line } from 'react-chartjs-2'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

interface Stats {
  todayRequests: number
  todayTokens: number
  activeUsers: number
  topModel: string
  requestsLast7Days: { day: string; count: number }[]
}

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    adminApi.getStats()
      .then(r => setStats(r.data))
      .finally(() => setLoading(false))
  }, [])

  const statCards = stats ? [
    { title: 'Requests Today', value: stats.todayRequests, icon: MessageSquare, color: 'text-blue-500' },
    { title: 'Tokens Used Today', value: stats.todayTokens.toLocaleString(), icon: Zap, color: 'text-yellow-500' },
    { title: 'Active Users', value: stats.activeUsers, icon: Users, color: 'text-green-500' },
    { title: 'Top Model', value: stats.topModel.split('/')[1]?.replace(':free', '') ?? stats.topModel, icon: Cpu, color: 'text-purple-500' },
  ] : []

  const chartData = {
    labels: stats?.requestsLast7Days.map(d => new Date(d.day).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })) ?? [],
    datasets: [{
      label: 'Requests',
      data: stats?.requestsLast7Days.map(d => d.count) ?? [],
      borderColor: 'hsl(221 83% 53%)',
      backgroundColor: 'hsl(221 83% 53% / 0.1)',
      tension: 0.4,
      fill: true,
    }],
  }

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">Dashboard</h1>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {loading ? Array(4).fill(0).map((_, i) => (
          <Card key={i}><CardContent className="p-6"><Skeleton className="h-16" /></CardContent></Card>
        )) : statCards.map(({ title, value, icon: Icon, color }) => (
          <Card key={title}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
              <Icon className={`w-4 h-4 ${color}`} />
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Chart */}
      <Card>
        <CardHeader>
          <CardTitle>Requests — Last 7 Days</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? <Skeleton className="h-64" /> : (
            <Line data={chartData} options={{
              responsive: true,
              plugins: { legend: { display: false } },
              scales: {
                y: { beginAtZero: true, ticks: { stepSize: 1 } }
              }
            }} />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
