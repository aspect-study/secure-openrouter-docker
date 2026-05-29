import { useEffect, useState } from 'react'
import { adminApi } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/utils'
import { toast } from 'sonner'
import { useAuth } from '@/hooks/useAuth'

interface User {
  id: number; email: string; role: string
  active: boolean; totalRequests: number; createdAt: string
}

export default function UserManagerPage() {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const { user: currentUser } = useAuth()

  useEffect(() => {
    adminApi.getUsers()
      .then(r => setUsers(r.data))
      .finally(() => setLoading(false))
  }, [])

  const updateRole = async (id: number, role: string) => {
    try {
      const r = await adminApi.updateUserRole(id, role)
      setUsers(prev => prev.map(u => u.id === id ? r.data : u))
      toast.success('Role updated')
    } catch { toast.error('Failed to update role') }
  }

  const updateStatus = async (id: number, active: boolean) => {
    try {
      const r = await adminApi.updateUserStatus(id, active)
      setUsers(prev => prev.map(u => u.id === id ? r.data : u))
      toast.success(active ? 'User activated' : 'User deactivated')
    } catch { toast.error('Failed to update status') }
  }

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-bold">User Manager</h1>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="text-left p-3 font-medium">Email</th>
                  <th className="text-left p-3 font-medium">Role</th>
                  <th className="text-right p-3 font-medium">Requests</th>
                  <th className="text-left p-3 font-medium">Joined</th>
                  <th className="text-center p-3 font-medium">Active</th>
                </tr>
              </thead>
              <tbody>
                {loading ? Array(5).fill(0).map((_, i) => (
                  <tr key={i} className="border-b border-border">
                    {Array(5).fill(0).map((_, j) => (
                      <td key={j} className="p-3"><Skeleton className="h-4" /></td>
                    ))}
                  </tr>
                )) : users.map(user => {
                  const isSelf = user.email === currentUser?.email
                  return (
                    <tr key={user.id} className="border-b border-border hover:bg-muted/30">
                      <td className="p-3">
                        <div className="flex items-center gap-2">
                          <span className="font-medium">{user.email}</span>
                          {isSelf && <Badge variant="outline" className="text-xs">You</Badge>}
                        </div>
                      </td>
                      <td className="p-3">
                        <Select
                          value={user.role}
                          onValueChange={v => updateRole(user.id, v)}
                          disabled={isSelf}
                        >
                          <SelectTrigger className="w-28 h-7 text-xs">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="USER">User</SelectItem>
                            <SelectItem value="ADMIN">Admin</SelectItem>
                          </SelectContent>
                        </Select>
                      </td>
                      <td className="p-3 text-right">{user.totalRequests}</td>
                      <td className="p-3 text-xs text-muted-foreground">
                        {formatDate(user.createdAt)}
                      </td>
                      <td className="p-3 flex justify-center">
                        <Switch
                          checked={user.active}
                          onCheckedChange={v => updateStatus(user.id, v)}
                          disabled={isSelf}
                        />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
