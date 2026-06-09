# API Reference

All endpoints are prefixed with `/api`. Requests requiring auth must include `Authorization: Bearer <jwt>`.

## Auth (public)

```
POST /api/auth/register          {"email": "...", "password": "..."}
POST /api/auth/login             {"email": "...", "password": "..."}
POST /api/auth/change-password   {"currentPassword": "...", "newPassword": "..."}  (requires JWT)
```

## Chat (requires JWT — ROLE_USER or ROLE_ADMIN)

```
POST /api/chat/completions                        {"model": "...", "messages": [...]}
GET  /api/chat/models                             Returns admin-enabled free model list
GET  /api/conversations                           List user's conversations
POST /api/conversations                           Create conversation {"model": "...", "title": "..."}
GET  /api/conversations/{id}                      Get conversation with messages
POST /api/conversations/{id}/messages             Send message (blocking, full response)
POST /api/conversations/{id}/messages/stream      Send message (SSE streaming)
DELETE /api/conversations/{id}                    Delete conversation
```

## SSE Stream Event Protocol — Conversation

```
event: token   data: "<JSON-encoded token string>"   (one per delta)
event: done    data: {"messageId":1,"conversationId":1,"title":"...","normalizedContent":"...","usage":{...}}
event: error   data: {"error":"...","remainingTokens":0}
```

Token data is JSON-encoded — raw `\n` in SSE `data:` fields is treated as an empty line by the protocol, silently dropping newlines. Backend: `objectMapper.writeValueAsString(token)`. Frontend: `JSON.parse(data)`.

## API Key — BYOK (requires JWT)

```
PUT    /api/user/api-key             {"apiKey": "sk-or-v1-..."} — validate + save
DELETE /api/user/api-key                                         — remove key
GET    /api/user/api-key/status                                  — {configured: true|false}
```

## User Model Preferences (requires JWT — ROLE_USER or ROLE_ADMIN)

```
GET    /api/user/models              — full model list with adminEnabled/userEnabled/effectivelyEnabled
PUT    /api/user/models/{id}/toggle  — atomically flip user preference; {id} = model_config integer PK
GET    /api/user/models/{id}/status  — single model state; {id} = model_config integer PK
```

**Note:** `{id}` is the `model_config` integer PK, not the string `modelId`. Model IDs contain `/` which Tomcat normalizes before Spring MVC sees the request — using the integer avoids this entirely.

**Response shape — `GET /api/user/models`:**
```json
{
  "models": [
    { "id": 3, "modelId": "meta-llama/...", "name": "Llama 3.3 70B", "adminEnabled": true, "userEnabled": true, "effectivelyEnabled": true }
  ],
  "totalAdminEnabled": 12,
  "totalUserEnabled": 8
}
```

`totalUserEnabled` = effective visible count (admin-enabled ∩ user-enabled). ROLE_ADMIN callers always receive all globally-enabled models regardless of preference rows.

## Admin (requires JWT — ROLE_ADMIN only)

```
GET  /api/admin/stats
GET  /api/admin/chat-logs?page=0&size=20&user=&model=&from=&to=
GET  /api/admin/chat-logs/export                              CSV download
GET  /api/admin/models
PUT  /api/admin/models/toggle         {"modelId": "..."} — toggle in request body
GET  /api/admin/users
PUT  /api/admin/users/{id}/role      {"role": "USER"|"ADMIN"}
PUT  /api/admin/users/{id}/status    {"active": true|false}
POST /api/admin/sync-models           → {"discovered":N,"added":N,"newModelIds":[...]}
```

## Agent (requires JWT — ROLE_ADMIN only, BYOK key required)

```
POST /api/agent/chat    — runs ReAct agent; streams progress as SSE, then a final done event
```

**Prerequisite:** User must have a configured BYOK API key. Returns 409 (`KeyNotConfiguredException`) if no key is set.

**Request body:**
```json
{
  "question": "Is llama-3.3-70b enabled and how much was it used today?",
  "model": "meta-llama/llama-3.3-70b-instruct:free",
  "history": [
    {"role": "user", "content": "What models are enabled?"},
    {"role": "assistant", "content": "Currently 8 models are enabled..."}
  ]
}
```

`model` defaults to `meta-llama/llama-3.3-70b-instruct:free`. `history` is optional — prior conversation turns prepended before the current question.

**SSE event protocol:**
```
event: status   data: {"type":"trying","model":"...","attempt":1,"total":12}
event: status   data: {"type":"skipped","model":"...","reason":"rate_limited"|"tool_unsupported"}
event: done     data: {"reply":"...","toolSteps":[...],"modelUsed":"..."}
event: error    data: {"error":"...","status":409|400|503|500}
```

Use `fetch` + `ReadableStream` — `EventSource` does not support POST.

## System

```
GET /actuator/health                 Spring Boot health (public)
GET http://localhost:8081/health     nginx proxy health
```
