// Browser text-to-speech with a distinct voice per panelist. Free and offline-capable; quality depends on the
// voices installed on the device. Swap `speak` for a streaming provider later without touching callers.
const PROFILES: Record<string, { pitch: number; rate: number; slot: number }> = {
  "hiring-manager": { pitch: 1.05, rate: 0.98, slot: 0 },
  "tech-lead": { pitch: 0.85, rate: 1.08, slot: 1 },
  skeptic: { pitch: 0.8, rate: 0.92, slot: 2 },
  "bar-raiser": { pitch: 0.95, rate: 0.95, slot: 3 },
  peer: { pitch: 1.15, rate: 1.04, slot: 4 },
};

export const ttsSupported = () => typeof window !== "undefined" && "speechSynthesis" in window;

function englishVoices(): SpeechSynthesisVoice[] {
  const rank = (v: SpeechSynthesisVoice) => (/natural|neural|online|premium|enhanced/i.test(v.name) ? 0 : v.localService ? 2 : 1);
  return speechSynthesis
    .getVoices()
    .filter((v) => v.lang.toLowerCase().startsWith("en"))
    .sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name));
}

let token = 0;

export function stopSpeaking() {
  token++;
  if (ttsSupported()) speechSynthesis.cancel();
}

/** Speaks `text` as the given panelist, sentence by sentence (natural pauses, avoids Chrome's long-utterance cutoff). */
export function speak(text: string, archetypeId: string | null | undefined, cb?: { onStart?: () => void; onEnd?: () => void }) {
  if (!ttsSupported() || !text.trim()) return cb?.onEnd?.();
  stopSpeaking();
  const my = token;
  const p = PROFILES[archetypeId ?? ""] ?? { pitch: 1, rate: 1, slot: 0 };
  const voices = englishVoices();
  const voice = voices.length ? voices[p.slot % voices.length] : undefined;
  const parts = text.replace(/\s+/g, " ").match(/[^.!?]+[.!?]*/g) ?? [text];
  parts.forEach((part, i) => {
    const u = new SpeechSynthesisUtterance(part.trim());
    if (voice) u.voice = voice;
    u.pitch = p.pitch;
    u.rate = p.rate;
    if (i === 0) u.onstart = () => my === token && cb?.onStart?.();
    if (i === parts.length - 1) u.onend = () => my === token && cb?.onEnd?.();
    u.onerror = () => my === token && i === parts.length - 1 && cb?.onEnd?.();
    speechSynthesis.speak(u);
  });
}

/** Voices load asynchronously in some browsers; call once on mount so the first line already has the right one. */
export function warmVoices() {
  if (ttsSupported()) speechSynthesis.getVoices();
}
