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

export interface ModelInfo {
  description: string
  who: string        // Who should use this model
  what: string       // What it excels at
  when: string       // When to use it
  where: string      // Where it fits in a workflow
  why: string        // Why choose it over others
  strengths: string[]
  limitations: string[]
  advantages: string[]
  disadvantages: string[]
  freeUsage: string  // How long / how much free usage
  context: string
  rpm: string
}

const MODEL_INFO: Record<string, ModelInfo> = {
  'nvidia/nemotron-nano-9b-v2:free': {
    description: 'NVIDIA\'s compact 9B parameter model optimized for fast inference. Best balance of speed and quality among NVIDIA\'s free offerings.',
    who: 'Developers needing quick responses for prototyping or lightweight integrations',
    what: 'General Q&A, summarization, simple text generation, classification tasks',
    when: 'When speed matters more than depth — real-time apps, chatbots, quick lookups',
    where: 'Front-line chat assistant, FAQ bots, content summarizers',
    why: 'Most reliable free NVIDIA model with consistent uptime and fast response times',
    strengths: ['Fast response (~2-4s)', 'High uptime', 'Good at following instructions', 'Consistent output quality'],
    limitations: ['Struggles with multi-step reasoning', 'Limited math capability', 'May hallucinate on niche topics'],
    advantages: ['Free tier — no cost', '128K context window', 'NVIDIA infrastructure reliability'],
    disadvantages: ['9B parameters — less capable than larger models', 'Not suitable for complex code generation'],
    freeUsage: '20 req/min, no daily cap — ideal for sustained usage',
    context: '128K', rpm: '20 req/min',
  },
  'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free': {
    description: 'A 30B reasoning-focused model from NVIDIA using sparse attention. Designed for chain-of-thought problems requiring logical deduction.',
    who: 'Students, researchers, analysts needing structured reasoning',
    what: 'Math problems, logic puzzles, step-by-step analysis, argument evaluation',
    when: 'When accuracy and reasoning transparency matter more than speed',
    where: 'Problem-solving workflows, tutoring systems, decision support',
    why: 'Explicit reasoning steps help catch errors and build trust in outputs',
    strengths: ['Transparent reasoning steps', 'Better accuracy on logic tasks', 'Good at math and science'],
    limitations: ['Significantly slower than non-reasoning models', 'Verbose output', 'Overkill for simple tasks'],
    advantages: ['Free access to reasoning capability', '128K context', 'Sparse architecture = efficient'],
    disadvantages: ['10-20s response time typical', 'High token usage due to reasoning traces'],
    freeUsage: '20 req/min — slower model so natural pacing',
    context: '128K', rpm: '20 req/min',
  },
  'nvidia/nemotron-3-super-120b-a12b:free': {
    description: 'NVIDIA\'s largest free model at 120B parameters with sparse MoE architecture. Highest quality output in the NVIDIA free lineup.',
    who: 'Power users needing near-GPT-4 quality without cost',
    what: 'Complex writing, nuanced analysis, detailed explanations, creative content',
    when: 'When quality is the top priority and latency is acceptable',
    where: 'Content creation, research synthesis, executive summaries',
    why: 'Largest free model available — closest to paid model quality',
    strengths: ['Highest output quality', 'Strong reasoning', 'Excellent writing style', 'Handles complex prompts well'],
    limitations: ['High latency (15-30s)', 'Heavy compute — may queue', 'Rate limited'],
    advantages: ['120B params for free', '128K context'],
    disadvantages: ['Slowest NVIDIA model', 'Not suitable for real-time use'],
    freeUsage: '10 req/min — use sparingly for high-value tasks',
    context: '128K', rpm: '10 req/min',
  },
  'meta-llama/llama-3.3-70b-instruct:free': {
    description: 'Meta\'s flagship instruction-tuned model. One of the best open-source models available, rivaling GPT-3.5 in most benchmarks.',
    who: 'Anyone — developers, writers, analysts, students',
    what: 'Virtually anything: chat, code, writing, analysis, translation',
    when: 'Default choice when unsure which model to use',
    where: 'General-purpose assistant, coding helper, document Q&A',
    why: 'Best all-around free model — proven, widely tested, reliable quality',
    strengths: ['Excellent instruction following', 'Strong code generation', 'Good at long context', 'Well-aligned responses'],
    limitations: ['Rate limited on free tier', 'Slower than smaller models', 'Occasional refusals on edge cases'],
    advantages: ['70B parameters free', '131K context', 'Meta\'s best open model'],
    disadvantages: ['Free tier rate limits hit quickly under load'],
    freeUsage: '20 req/min — monitor usage in high-traffic scenarios',
    context: '131K', rpm: '20 req/min',
  },
  'meta-llama/llama-3.2-3b-instruct:free': {
    description: 'Meta\'s lightweight 3B model optimized for speed and efficiency. Ideal for edge deployments and latency-sensitive applications.',
    who: 'Developers building high-throughput, low-latency applications',
    what: 'Simple Q&A, classification, short text generation, quick lookups',
    when: 'When you need sub-second responses and tasks are straightforward',
    where: 'Mobile apps, embedded assistants, high-volume pipelines',
    why: 'Fastest response time among free models — ideal for interactive use',
    strengths: ['Very fast (<1s typical)', 'High request capacity', 'Low resource footprint'],
    limitations: ['Limited reasoning depth', 'Short effective context in practice', 'Less nuanced than larger models'],
    advantages: ['30 req/min free', '131K context', 'Extremely fast'],
    disadvantages: ['3B params — noticeably less capable for complex tasks'],
    freeUsage: '30 req/min — highest free RPM, great for volume use',
    context: '131K', rpm: '30 req/min',
  },
  'deepseek/deepseek-v4-flash:free': {
    description: 'DeepSeek\'s fast inference model tuned for coding and structured output. Excellent at JSON generation and code tasks.',
    who: 'Developers, data engineers needing structured output or code help',
    what: 'Code generation, debugging, SQL queries, JSON/YAML generation, API design',
    when: 'When working on technical tasks requiring precise structured output',
    where: 'Development workflows, CI automation, code review assistance',
    why: 'Best free model for code tasks — trained extensively on programming content',
    strengths: ['Superior code quality', 'Reliable structured output (JSON/YAML)', 'Good at following format specs', 'Fast for its size'],
    limitations: ['Upstream rate limits from Venice provider', 'Less effective for creative writing', 'Occasional 429 errors'],
    advantages: ['163K context — largest free context window', 'Code-optimized training'],
    disadvantages: ['Dependent on Venice provider — rate limits unpredictable'],
    freeUsage: '20 req/min but upstream throttling may apply — have fallback ready',
    context: '163K', rpm: '20 req/min',
  },
  'qwen/qwen3-coder:free': {
    description: 'Alibaba\'s specialized coding model. Fine-tuned specifically for code generation, refactoring, and technical documentation.',
    who: 'Software developers, code reviewers, technical writers',
    what: 'Code generation in 40+ languages, refactoring, bug fixing, code explanation',
    when: 'When code quality and correctness are the primary requirements',
    where: 'IDE assistants, code review tools, documentation generators',
    why: 'Purpose-built for code — outperforms general models on programming benchmarks',
    strengths: ['40+ programming languages', 'Strong at refactoring', 'Good test generation', 'Understands frameworks'],
    limitations: ['Not suitable for creative or general writing', 'Can over-engineer simple solutions'],
    advantages: ['Specialized training on code corpora', '131K context'],
    disadvantages: ['Narrow focus — poor outside technical domains'],
    freeUsage: '20 req/min — sustainable for active development',
    context: '131K', rpm: '20 req/min',
  },
  'google/gemma-4-31b-it:free': {
    description: 'Google\'s 31B instruction-tuned model from the Gemma 4 series. Strong at following complex instructions and long-form tasks.',
    who: 'Content creators, researchers, analysts needing reliable instruction following',
    what: 'Summarization, document analysis, instruction-following tasks, Q&A over documents',
    when: 'When working with long documents or needing precise instruction adherence',
    where: 'Document processing pipelines, customer support, knowledge bases',
    why: 'Google-quality instruction tuning with strong safety alignment',
    strengths: ['Excellent instruction following', 'Good at long documents', 'Strong safety alignment', 'Consistent formatting'],
    limitations: ['Less creative than some alternatives', 'Conservative on edge cases'],
    advantages: ['Google infrastructure', '128K context', 'Strong multilingual support'],
    disadvantages: ['Occasionally over-cautious refusals'],
    freeUsage: '20 req/min — reliable and consistent',
    context: '128K', rpm: '20 req/min',
  },
  'openai/gpt-oss-120b:free': {
    description: 'OpenAI\'s open-source 120B model made available via OpenRouter. Near GPT-4 quality for free in supported tasks.',
    who: 'Users who need premium quality output without a paid subscription',
    what: 'Complex reasoning, long-form writing, nuanced analysis, multi-step problems',
    when: 'For high-stakes tasks where response quality is critical',
    where: 'Research, executive communication, complex technical documentation',
    why: 'Closest to GPT-4 quality available on the free tier',
    strengths: ['Near GPT-4 output quality', '200K context', 'Excellent at nuanced tasks', 'Strong reasoning'],
    limitations: ['Very high latency (20-40s)', 'Heavy resource usage', 'Low rate limit'],
    advantages: ['200K context — largest available', 'OpenAI training quality'],
    disadvantages: ['Slowest model available — not for real-time use', 'Only 10 req/min'],
    freeUsage: '10 req/min — reserve for your most important requests',
    context: '200K', rpm: '10 req/min',
  },
  'openai/gpt-oss-20b:free': {
    description: 'OpenAI\'s 20B open-source model — a faster, lighter version of the 120B. Good balance of quality and speed.',
    who: 'Users wanting OpenAI quality with faster turnaround',
    what: 'General tasks, writing assistance, moderate complexity analysis',
    when: 'When 120b is too slow but quality matters more than raw speed',
    where: 'Daily assistant tasks, writing help, general Q&A',
    why: 'OpenAI\'s training methodology at a more accessible speed/cost point',
    strengths: ['OpenAI training quality', 'Faster than 120b', '200K context', 'Good instruction following'],
    limitations: ['Less powerful than 120b for complex tasks'],
    advantages: ['200K context at 20 req/min', 'OpenAI quality signal'],
    disadvantages: ['Still slower than open-source alternatives of similar size'],
    freeUsage: '20 req/min — good daily driver for OpenAI-style responses',
    context: '200K', rpm: '20 req/min',
  },
  'moonshotai/kimi-k2.6:free': {
    description: 'Moonshot AI\'s model with exceptional long-context capabilities. Best-in-class for very long document processing.',
    who: 'Researchers, lawyers, analysts working with lengthy documents',
    what: 'Long document Q&A, book summarization, multi-document analysis, research synthesis',
    when: 'When documents exceed what other models can handle effectively',
    where: 'Legal review, research pipelines, long-form content analysis',
    why: 'Optimized specifically for long-context retention and recall',
    strengths: ['Excellent long-document recall', 'Strong Chinese + English support', 'Good at synthesis across sources'],
    limitations: ['Slower response times', 'Rate limited — only 10 req/min', 'Less effective on short tasks'],
    advantages: ['131K context with strong retention', 'Bilingual capability'],
    disadvantages: ['Low RPM makes it unsuitable for high-volume use'],
    freeUsage: '10 req/min — best for batch document processing, not interactive chat',
    context: '131K', rpm: '10 req/min',
  },
  'liquid/lfm-2.5-1.2b-instruct:free': {
    description: 'Liquid AI\'s ultra-compact 1.2B instruction model. Fastest possible response times for simple tasks.',
    who: 'Developers needing an extremely fast, lightweight model for simple automation',
    what: 'Classification, simple Q&A, keyword extraction, short text tasks',
    when: 'When latency is critical and tasks are straightforward',
    where: 'Edge devices, mobile, high-frequency automation pipelines',
    why: 'Fastest available model — sub-second responses even on simple hardware',
    strengths: ['Extremely fast (<500ms)', 'Highest RPM (30/min)', 'Low compute requirements'],
    limitations: ['1.2B params — very limited capability', 'Poor at multi-step tasks', 'Short effective attention span'],
    advantages: ['30 req/min free', 'Ultra-low latency'],
    disadvantages: ['Not suitable for anything requiring depth or reasoning'],
    freeUsage: '30 req/min — use for volume simple tasks only',
    context: '32K', rpm: '30 req/min',
  },
  'nousresearch/hermes-3-llama-3.1-405b:free': {
    description: 'NousResearch\'s fine-tune of Meta\'s 405B Llama model. The largest free model available — premium quality for complex structured tasks.',
    who: 'Power users, researchers, developers needing the absolute best free output',
    what: 'Complex structured output (JSON schemas), agentic tasks, advanced reasoning, function calling',
    when: 'For the most demanding tasks where quality justifies the wait',
    where: 'Agent pipelines, complex data extraction, multi-step reasoning chains',
    why: 'Largest open model fine-tuned for instruction following and structured output',
    strengths: ['405B parameters', 'Excellent structured output', 'Strong function calling', 'Best reasoning depth'],
    limitations: ['Very high latency (30-60s)', 'Only 5 req/min', 'May queue during peak hours'],
    advantages: ['Largest free model', '131K context', 'Hermes fine-tuning for better instruction following'],
    disadvantages: ['Lowest RPM — 5 req/min only', 'Impractical for interactive use'],
    freeUsage: '5 req/min — use exclusively for batch high-value tasks, never real-time',
    context: '131K', rpm: '5 req/min',
  },
  'openrouter/free': {
    description: 'OpenRouter\'s smart routing model that automatically selects the best available free model for each request.',
    who: 'Users who want optimal model selection without manually choosing',
    what: 'Anything — it routes to the best free model available at that moment',
    when: 'When you don\'t care which model handles the request, just want the best result',
    where: 'General-purpose use, experimentation, when other models are rate-limited',
    why: 'Automatic fallback and load balancing across free models',
    strengths: ['Automatic best-model selection', 'Built-in fallback', 'No model management needed'],
    limitations: ['No guaranteed model — response style varies', 'Can\'t predict which model handles your request'],
    advantages: ['Always routes to available models', 'Zero configuration'],
    disadvantages: ['Inconsistent style across requests', 'Can\'t reproduce exact outputs'],
    freeUsage: 'Varies — depends on which model is selected for each request',
    context: 'Varies', rpm: 'Varies',
  },
}

export function modelInfo(modelId: string): ModelInfo {
  return MODEL_INFO[modelId] ?? {
    description: 'A free model available via OpenRouter.',
    who: 'General users',
    what: 'General purpose tasks',
    when: 'When other models are unavailable',
    where: 'Any workflow',
    why: 'Free access',
    strengths: ['Free to use'],
    limitations: ['Details not available'],
    advantages: ['No cost'],
    disadvantages: ['Limited documentation'],
    freeUsage: '20 req/min estimated',
    context: '32K',
    rpm: '20 req/min',
  }
}
