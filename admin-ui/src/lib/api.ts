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
  // Usage limits
  getGlobalLimits: () => api.get('/admin/usage/limits'),
  setGlobalLimit: (modelId: string, maxRequestsPerDay: number, maxTokensPerDay: number) =>
    api.put(`/admin/usage/limits/${encodeURIComponent(modelId)}`, { maxRequestsPerDay, maxTokensPerDay }),
  getUserLimits: (userId: number) => api.get(`/admin/users/${userId}/usage/limits`),
  setUserLimit: (userId: number, modelId: string, maxRequestsPerDay: number, maxTokensPerDay: number) =>
    api.put(`/admin/users/${userId}/usage/limits/${encodeURIComponent(modelId)}`, { maxRequestsPerDay, maxTokensPerDay }),
  deleteUserLimit: (userId: number, modelId: string) =>
    api.delete(`/admin/users/${userId}/usage/limits/${encodeURIComponent(modelId)}`),
  getUserUsage: (userId: number) => api.get(`/admin/users/${userId}/usage`),
}

// ── API Key (per-user BYOK) ───────────────────────────────────────────────
export const apiKeyApi = {
  getStatus: () => api.get('/user/api-key/status'),
  saveKey: (apiKey: string) => api.put('/user/api-key', { apiKey }),
  removeKey: () => api.delete('/user/api-key'),
}

// ── Usage ─────────────────────────────────────────────────────────────────
export const usageApi = {
  getTodayUsage: () => api.get('/user/usage'),
  getModelUsage: (modelId: string) => api.get(`/user/usage/${encodeURIComponent(modelId)}`),
}

// ── User Model Preferences (PRD-003) ──────────────────────────────────────
// All toggle/status calls use the model_config integer PK (UserModelDto.id).
// The modelId string is for display only and must NEVER appear in a URL path
// (model IDs contain forward slashes that Tomcat normalises before Spring MVC
// sees the request — URL-encoding does not reliably solve this).
export const userModelApi = {
  /** GET /api/user/models — full model list with admin + user state for each entry */
  getModels: () => api.get('/user/models'),

  /** PUT /api/user/models/{id}/toggle — atomically flip the user's preference */
  toggleModel: (id: number) => api.put(`/user/models/${id}/toggle`),

  /** GET /api/user/models/{id}/status — current preference state for one model */
  getModelStatus: (id: number) => api.get(`/user/models/${id}/status`),
}
