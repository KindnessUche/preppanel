export type ProviderId = "anthropic" | "openai" | "gemini" | "xai" | "groq";

export interface ProviderOption {
  id: ProviderId;
  label: string;
  defaultModel: string;
  /** Suggestions only - the model field is free text because model names change often. */
  models: string[];
  keyUrl: string;
  keyPlaceholder: string;
}

export const PROVIDERS: ProviderOption[] = [
  {
    id: "anthropic",
    label: "Claude (Anthropic)",
    defaultModel: "claude-sonnet-5-5",
    models: ["claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5"],
    keyUrl: "https://console.anthropic.com/settings/keys",
    keyPlaceholder: "sk-ant-…",
  },
  {
    id: "openai",
    label: "ChatGPT (OpenAI)",
    defaultModel: "gpt-5.5",
    models: ["gpt-5.5", "gpt-5.4-mini", "chat-latest"],
    keyUrl: "https://platform.openai.com/api-keys",
    keyPlaceholder: "sk-…",
  },
  {
    id: "gemini",
    label: "Gemini (Google)",
    defaultModel: "gemini-3.1-flash-lite",
    models: ["gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-2.5-pro"],
    keyUrl: "https://aistudio.google.com/apikey",
    keyPlaceholder: "AIza…",
  },
  {
    id: "xai",
    label: "Grok (xAI)",
    defaultModel: "grok-4.3",
    models: ["grok-4.3", "grok-4.5", "grok-4.7"],
    keyUrl: "https://console.x.ai",
    keyPlaceholder: "xai-…",
  },
  {
    id: "groq",
    label: "Groq (free tier)",
    defaultModel: "llama-3.3-70b-versatile",
    models: ["llama-3.3-70b-versatile"],
    keyUrl: "https://console.groq.com/keys",
    keyPlaceholder: "gsk_…",
  },
];

export function getProvider(id: string): ProviderOption {
  return PROVIDERS.find((p) => p.id === id) ?? PROVIDERS[0];
}
