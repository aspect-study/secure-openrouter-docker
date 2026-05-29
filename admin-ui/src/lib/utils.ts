import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString('en-PH', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export function formatTime(dateStr: string): string {
  return new Date(dateStr).toLocaleTimeString('en-PH', {
    hour: '2-digit', minute: '2-digit',
  })
}

export function truncate(str: string, maxLen: number): string {
  return str && str.length > maxLen ? str.substring(0, maxLen) + '...' : str
}

export function modelEmoji(modelId: string): string {
  if (modelId.startsWith('nvidia/')) return '🧠'
  if (modelId.startsWith('meta-llama/')) return '🦙'
  if (modelId.startsWith('deepseek/')) return '🌊'
  if (modelId.startsWith('google/')) return '💎'
  if (modelId.startsWith('openai/')) return '⚡'
  if (modelId.startsWith('qwen/')) return '🔮'
  if (modelId.startsWith('poolside/')) return '🏊'
  if (modelId.startsWith('liquid/')) return '💧'
  if (modelId.startsWith('moonshotai/')) return '🌙'
  if (modelId.startsWith('z-ai/')) return '🤖'
  if (modelId.startsWith('openrouter/')) return '🦉'
  return '🤖'
}

export function modelCapability(modelId: string): string {
  if (modelId.includes('coder') || modelId.includes('code')) return 'Coding'
  if (modelId.includes('reasoning') || modelId.includes('r1')) return 'Logic'
  if (modelId.includes('instruct')) return 'Balanced'
  if (modelId.includes('creative')) return 'Creative'
  return 'General'
}

export function modelDisplayName(modelId: string): string {
  const parts = modelId.split('/')
  if (parts.length < 2) return modelId
  return parts[1].replace(/:free$/, '').replace(/-/g, ' ')
}

const MODEL_DESCRIPTIONS: Record<string, { use: string; limit: string; context: string; rpm: string }> = {
  'nvidia/nemotron-nano-9b-v2:free':                           { use: 'Fast general tasks, Q&A, summarization',      limit: 'Weaker at complex reasoning',          context: '128K', rpm: '20 req/min' },
  'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free':        { use: 'Step-by-step reasoning, math, logic',         limit: 'Slower due to reasoning steps',        context: '128K', rpm: '20 req/min' },
  'nvidia/nemotron-3-super-120b-a12b:free':                    { use: 'High-quality text generation, analysis',      limit: 'High latency, heavy model',            context: '128K', rpm: '10 req/min' },
  'nvidia/nemotron-3-nano-30b-a3b:free':                       { use: 'Balanced everyday tasks',                     limit: 'Not specialized for code or math',     context: '128K', rpm: '20 req/min' },
  'nvidia/nemotron-nano-12b-v2-vl:free':                       { use: 'Vision + text tasks, image description',      limit: 'Limited context window',               context: '32K',  rpm: '20 req/min' },
  'meta-llama/llama-3.3-70b-instruct:free':                    { use: 'Best all-rounder, chat, writing, code',       limit: 'Rate limited on free tier',            context: '131K', rpm: '20 req/min' },
  'meta-llama/llama-3.2-3b-instruct:free':                     { use: 'Lightweight, fast responses',                 limit: 'Less capable than 70b variant',        context: '131K', rpm: '30 req/min' },
  'deepseek/deepseek-v4-flash:free':                           { use: 'Fast coding, structured output',              limit: 'Occasional upstream rate limits',      context: '163K', rpm: '20 req/min' },
  'qwen/qwen3-coder:free':                                     { use: 'Code generation, debugging, refactoring',     limit: 'Less effective for creative writing',  context: '131K', rpm: '20 req/min' },
  'qwen/qwen3-next-80b-a3b-instruct:free':                     { use: 'Multilingual tasks, long documents',          limit: 'Rate limited on free tier',            context: '131K', rpm: '20 req/min' },
  'google/gemma-4-31b-it:free':                                { use: 'Instruction following, summarization',        limit: 'Limited complex reasoning',            context: '128K', rpm: '20 req/min' },
  'google/gemma-4-26b-a4b-it:free':                            { use: 'General chat, text analysis',                 limit: 'Smaller than 31b variant',             context: '128K', rpm: '20 req/min' },
  'openai/gpt-oss-120b:free':                                  { use: 'Complex reasoning, long context tasks',       limit: 'High latency, heavy resource use',     context: '200K', rpm: '10 req/min' },
  'openai/gpt-oss-20b:free':                                   { use: 'Balanced speed and capability',               limit: 'Less powerful than 120b',              context: '200K', rpm: '20 req/min' },
  'poolside/laguna-xs.2:free':                                 { use: 'Quick code completions',                      limit: 'Smaller context, less capable',        context: '32K',  rpm: '20 req/min' },
  'poolside/laguna-m.1:free':                                  { use: 'Code generation, debugging',                  limit: 'May lag on complex architectures',     context: '32K',  rpm: '20 req/min' },
  'liquid/lfm-2.5-1.2b-thinking:free':                         { use: 'Lightweight reasoning tasks',                 limit: 'Very small model, limited depth',      context: '32K',  rpm: '30 req/min' },
  'liquid/lfm-2.5-1.2b-instruct:free':                         { use: 'Fast simple instructions',                   limit: 'Not suitable for complex tasks',       context: '32K',  rpm: '30 req/min' },
  'moonshotai/kimi-k2.6:free':                                 { use: 'Long document analysis, research',            limit: 'Rate limited, slower responses',       context: '131K', rpm: '10 req/min' },
  'z-ai/glm-4.5-air:free':                                     { use: 'Chinese + English bilingual tasks',           limit: 'Less optimized for English-only use',  context: '128K', rpm: '20 req/min' },
  'cognitivecomputations/dolphin-mistral-24b-venice-edition:free': { use: 'Uncensored creative writing, roleplay', limit: 'May produce unfiltered content',        context: '32K',  rpm: '20 req/min' },
  'nousresearch/hermes-3-llama-3.1-405b:free':                 { use: 'Complex instructions, structured output',     limit: 'Very high latency, rate limited',      context: '131K', rpm: '5 req/min'  },
  'openrouter/owl-alpha':                                       { use: 'OpenRouter experimental routing',            limit: 'Alpha — may be unstable',              context: 'Varies', rpm: 'Varies'    },
  'openrouter/free':                                            { use: 'Auto-routes to best available free model',   limit: 'No guaranteed model consistency',      context: 'Varies', rpm: 'Varies'    },
}

export function modelDescription(modelId: string): { use: string; limit: string; context: string; rpm: string } {
  return MODEL_DESCRIPTIONS[modelId] ?? { use: 'General purpose model', limit: 'Performance varies', context: '32K', rpm: '20 req/min' }
}
