"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { ApiError, readJsonOrThrow } from "@/lib/api";
import { PROVIDERS, getProvider, type ProviderId } from "@/lib/llm-providers";
import {
  clearSettings,
  DEFAULT_SETTINGS,
  headersFor,
  loadSettings,
  saveSettings,
  type AppSettings,
} from "@/lib/settings-store";

type TestState = { kind: "idle" } | { kind: "testing" } | { kind: "ok" } | { kind: "error"; message: string };

const inputClass =
  "w-full rounded-md border border-line bg-transparent px-3 py-2 text-sm text-ink outline-none focus:border-lineStrong";
const buttonClass =
  "shrink-0 rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong disabled:cursor-not-allowed disabled:opacity-60";

export default function ApiKeysPanel() {
  const { authFetch } = useAuth();
  const [settings, setSettings] = useState<AppSettings>(DEFAULT_SETTINGS);
  const [loaded, setLoaded] = useState(false);
  const [showKey, setShowKey] = useState(false);
  const [showAai, setShowAai] = useState(false);
  const [saved, setSaved] = useState(false);
  const [test, setTest] = useState<TestState>({ kind: "idle" });

  // localStorage only exists in the browser, so load after mount.
  useEffect(() => {
    setSettings(loadSettings());
    setLoaded(true);
  }, []);

  const provider = getProvider(settings.activeProvider);
  const key = settings.keys[settings.activeProvider] ?? "";
  const model = settings.models[settings.activeProvider] ?? "";

  function update(patch: Partial<AppSettings>) {
    setSettings((s) => ({ ...s, ...patch }));
    setSaved(false);
    setTest({ kind: "idle" });
  }

  function setKey(value: string) {
    update({ keys: { ...settings.keys, [settings.activeProvider]: value } });
  }

  function setModel(value: string) {
    update({ models: { ...settings.models, [settings.activeProvider]: value } });
  }

  function handleSave() {
    saveSettings({
      ...settings,
      keys: Object.fromEntries(Object.entries(settings.keys).map(([k, v]) => [k, (v ?? "").trim()])),
      assemblyAiKey: settings.assemblyAiKey.trim(),
    });
    setSaved(true);
  }

  async function handleTest() {
    setTest({ kind: "testing" });
    try {
      // Test with what's in the form right now, even if it isn't saved yet.
      const res = await authFetch("/api/llm/test", {
        method: "POST",
        headers: headersFor(settings.activeProvider as ProviderId, key, model),
      });
      await readJsonOrThrow(res);
      setTest({ kind: "ok" });
    } catch (err) {
      setTest({ kind: "error", message: err instanceof ApiError ? err.message : "Couldn't reach the server." });
    }
  }

  function handleClear() {
    if (!window.confirm("Remove all saved API keys from this browser?")) return;
    clearSettings();
    setSettings(DEFAULT_SETTINGS);
    setSaved(false);
    setTest({ kind: "idle" });
  }

  if (!loaded) return null;

  return (
    <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
      <span className="fig-label">API KEYS</span>
      <p className="mt-2 text-sm text-muted">
        Bring your own keys. They&apos;re saved only in this browser — the server uses them to call the AI for you
        while handling each request, and doesn&apos;t store or log them.
      </p>

      {/* ---- reasoning model ---- */}
      <div className="mt-5 border-t border-line pt-4">
        <p className="text-xs font-medium text-ink">Reasoning model</p>
        <p className="mt-1 text-xs text-muted">Runs the interviewers and scores your answers.</p>

        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-xs text-muted">Provider</span>
            <select
              value={settings.activeProvider}
              onChange={(e) => update({ activeProvider: e.target.value as ProviderId })}
              className={`${inputClass} mt-1 bg-canvas`}
            >
              {PROVIDERS.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.label}
                  {settings.keys[p.id]?.trim() ? "  ✓" : ""}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="text-xs text-muted">Model</span>
            <input
              list="model-suggestions"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder={provider.defaultModel}
              className={`${inputClass} mt-1`}
            />
            <datalist id="model-suggestions">
              {provider.models.map((m) => (
                <option key={m} value={m} />
              ))}
            </datalist>
          </label>
        </div>

        <label className="mt-3 block">
          <span className="text-xs text-muted">
            {provider.label} API key —{" "}
            <a href={provider.keyUrl} target="_blank" rel="noreferrer" className="underline hover:text-ink">
              get one
            </a>
          </span>
          <div className="mt-1 flex gap-2">
            <input
              type={showKey ? "text" : "password"}
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder={provider.keyPlaceholder}
              autoComplete="off"
              spellCheck={false}
              className={`${inputClass} min-w-0 flex-1`}
            />
            <button type="button" onClick={() => setShowKey((v) => !v)} className={buttonClass}>
              {showKey ? "Hide" : "Show"}
            </button>
          </div>
        </label>

        <p className="mt-2 text-[11px] text-faint">
          Model names change often — if a request fails with &quot;model not available&quot;, type the current name
          from the provider&apos;s docs.
        </p>
      </div>

      {/* ---- voice ---- */}
      <div className="mt-5 border-t border-line pt-4">
        <p className="text-xs font-medium text-ink">Voice (speech-to-text)</p>
        <p className="mt-1 text-xs text-muted">AssemblyAI turns your spoken answers into live text as you talk.</p>

        <label className="mt-3 block">
          <span className="text-xs text-muted">
            AssemblyAI API key —{" "}
            <a
              href="https://www.assemblyai.com/dashboard/api-keys"
              target="_blank"
              rel="noreferrer"
              className="underline hover:text-ink"
            >
              get one
            </a>
          </span>
          <div className="mt-1 flex gap-2">
            <input
              type={showAai ? "text" : "password"}
              value={settings.assemblyAiKey}
              onChange={(e) => update({ assemblyAiKey: e.target.value })}
              autoComplete="off"
              spellCheck={false}
              className={`${inputClass} min-w-0 flex-1`}
            />
            <button type="button" onClick={() => setShowAai((v) => !v)} className={buttonClass}>
              {showAai ? "Hide" : "Show"}
            </button>
          </div>
        </label>

        <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-3">
          <label className="flex items-center gap-2 text-xs text-ink">
            <input
              type="checkbox"
              checked={settings.autoListen}
              onChange={(e) => update({ autoListen: e.target.checked })}
            />
            Start listening automatically when a question appears
          </label>
          <label className="flex items-center gap-2 text-xs text-ink">
            <input
              type="checkbox"
              checked={settings.autoSend}
              onChange={(e) => update({ autoSend: e.target.checked })}
            />
            Send my answer automatically after a pause
          </label>
          <label className="flex items-center gap-2 text-xs text-muted">
            Pause length
            <select
              value={settings.silenceSeconds}
              disabled={!settings.autoSend}
              onChange={(e) => update({ silenceSeconds: Number(e.target.value) })}
              className="rounded-md border border-line bg-canvas px-2 py-1 text-xs text-ink disabled:opacity-50"
            >
              {[2, 3, 4, 5, 6].map((n) => (
                <option key={n} value={n}>
                  {n} seconds
                </option>
              ))}
            </select>
          </label>
        </div>
        <p className="mt-2 text-[11px] text-faint">
          If you tend to pause while thinking, pick a longer pause. Speaking again before the countdown ends keeps
          your answer going.
        </p>
      </div>

      {/* ---- actions ---- */}
      <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-line pt-4">
        <button onClick={handleSave} className={buttonClass}>
          Save
        </button>
        <button onClick={handleTest} disabled={!key.trim() || test.kind === "testing"} className={buttonClass}>
          {test.kind === "testing" ? "Testing…" : "Test reasoning key"}
        </button>
        <button onClick={handleClear} className="ml-auto font-mono text-xs text-faint hover:text-red-400">
          Remove all keys
        </button>
      </div>

      {saved && <p className="mt-3 text-xs text-accent">Saved in this browser.</p>}
      {test.kind === "ok" && <p className="mt-3 text-xs text-accent">Connected — that key works.</p>}
      {test.kind === "error" && <p className="mt-3 text-xs text-red-400">{test.message}</p>}

      <p className="mt-4 text-[11px] leading-relaxed text-faint">
        Heads up: keys in browser storage can be read by anyone with access to this browser profile. If you deploy
        this app, serve it over HTTPS so keys aren&apos;t sent in the clear.
      </p>
    </div>
  );
}
