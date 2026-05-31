import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT from localStorage to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Auto-logout on 401 only when there was an active session.
// Do NOT redirect on 401 from auth endpoints (wrong password is a 401 too).
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const is401 = error.response?.status === 401
    const isAuthEndpoint = error.config?.url?.includes('/auth/')
    const hadToken = !!localStorage.getItem('token')

    if (is401 && !isAuthEndpoint && hadToken) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }

    return Promise.reject(error)
  }
)

export default api

// ── Auth ──────────────────────────────────────────────────────────────────
export const authApi = {
  login: (email: string, password: string) =>
    api.post('/auth/login', { email, password }),
  register: (email: string, password: string) =>
    api.post('/auth/register', { email, password }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post('/auth/change-password', { currentPassword, newPassword }),
}

// ── Chat / Conversations ──────────────────────────────────────────────────
export const chatApi = {
  getModels: () => api.get('/chat/models'),
  getConversations: () => api.get('/conversations'),
  createConversation: (model: string, title?: string) =>
    api.post('/conversations', { model, title }),
  getConversation: (id: number) => api.get(`/conversations/${id}`),
  sendMessage: (conversationId: number, content: string) =>
    api.post(`/conversations/${conversationId}/messages`, { content }),
  deleteConversation: (id: number) => api.delete(`/conversations/${id}`),
}

// ── Admin ─────────────────────────────────────────────────────────────────
export const adminApi = {
  getStats: () => api.get('/admin/stats'),
  getChatLogs: (params: Record<string, unknown>) =>
    api.get('/admin/chat-logs', { params }),
  exportChatLogs: () =>
    api.get('/admin/chat-logs/export', { responseType: 'blob' }),
  getModels: () => api.get('/admin/models'),
  toggleModel: (modelId: string) =>
    api.put('/admin/models/toggle', { modelId }),
  getUsers: () => api.get('/admin/users'),
  updateUserRole: (id: number, role: string) =>
    api.put(`/admin/users/${id}/role`, { role }),
  updateUserStatus: (id: number, active: boolean) =>
    api.put(`/admin/users/${id}/status`, { active }),
}
