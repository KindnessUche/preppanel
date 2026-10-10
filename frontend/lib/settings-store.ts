import { getProvider, type ProviderId } from "@/lib/llm-providers";

/**
 * Everything here lives ONLY in this browser's localStorage. The backend never stores these keys;
 * they're sent in request headers so it can call the provider on your behalf, then forgotten.
 *
 * Tradeoff worth knowing: localStorage is readable by any script running on this site, so a
 * cross-site-scripting bug would expose these keys (unlike the login refresh token, which is in an
 * httpOnly cookie). Fine for a local/personal tool; revisit before offering this to the public.
 */
const STORAGE_KEY = "preppanel_settings_v1";

export interface AppSettings {
  activeProvider: ProviderId;
  keys: Partial<Record<ProviderId, string>>;
  models: Partial<Record<ProviderId, string>>;
  assemblyAiKey: string;
  /** Hands-free: start listening automatically when each question appears. */
  autoListen: boolean;
  /** Hands-free: send the answer automatically after a pause. */
  autoSend: boolean;
  silenceSeconds: number;
}

export const DEFAULT_SETTINGS: AppSettings = {
  activeProvider: "anthropic",
  keys: {},
  models: {},
  assemblyAiKey: "",
  autoListen: true,
  autoSend: true,
  silenceSeconds: 3,
};

export function loadSettings(): AppSettings {
  if (typeof window === "undefined") return DEFAULT_SETTINGS;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(settings: AppSettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    /* storage full or blocked - nothing sensible to do */
  }
}

export function clearSettings(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

export function hasReasoningKey(settings: AppSettings = loadSettings()): boolean {
  return !!settings.keys[settings.activeProvider]?.trim();
}

/** Builds the headers the backend reads (see CredentialHeaderFilter). Empty if no key is saved. */
export function headersFor(provider: ProviderId, key: string, model: string): Record<string, string> {
  if (!key.trim()) return {};
  return {
    "X-LLM-Provider": provider,
    "X-LLM-Key": key.trim(),
    "X-LLM-Model": model.trim() || getProvider(provider).defaultModel,
  };
}

export function llmHeaders(): Record<string, string> {
  const s = loadSettings();
  const provider = s.activeProvider;
  return headersFor(provider, s.keys[provider] ?? "", s.models[provider] ?? "");
}

export function assemblyAiHeaders(): Record<string, string> {
  const key = loadSettings().assemblyAiKey.trim();
  return key ? { "X-AssemblyAI-Key": key } : {};
}
