import type { AuthFetch } from "@/lib/interviews";
import { readJsonOrThrow } from "@/lib/api";
import { assemblyAiHeaders } from "@/lib/settings-store";

export interface AudioMetrics {
  wordsPerMinute: number;
  fillerWordCount: number;
  pauseCount: number;
  durationSeconds: number;
}

export interface TranscriptionResponse {
  text: string;
  audioMetrics: AudioMetrics;
}

export interface StreamWord {
  text: string;
  startMs: number;
  endMs: number;
}

export interface StreamingToken {
  token: string;
  websocketUrl: string;
  /** null = AssemblyAI's default streaming model. */
  speechModel: string | null;
}

/**
 * Live transcription: the backend swaps your AssemblyAI key for a short-lived token, so the real key
 * never reaches the page. The browser then streams audio straight to AssemblyAI.
 */
export async function fetchStreamingToken(authFetch: AuthFetch): Promise<StreamingToken> {
  const res = await authFetch("/api/transcribe/token", { method: "GET", headers: assemblyAiHeaders() });
  return readJsonOrThrow<StreamingToken>(res);
}

/** Delivery metrics (pace, fillers, pauses) from the word timestamps received while streaming. */
export async function computeMetrics(
  authFetch: AuthFetch,
  words: StreamWord[],
  durationSeconds: number
): Promise<AudioMetrics> {
  const res = await authFetch("/api/transcribe/metrics", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ words: words.slice(0, 3000), durationSeconds: Math.min(900, Math.max(0, durationSeconds)) }),
  });
  return readJsonOrThrow<AudioMetrics>(res);
}

/** Record-then-upload path. No longer used by the UI (live streaming replaced it) but the endpoint still exists. */
export async function transcribeAudio(authFetch: AuthFetch, audio: Blob): Promise<TranscriptionResponse> {
  const ext = audio.type.includes("mp4") ? "m4a" : "webm";
  const formData = new FormData();
  formData.append("file", audio, `answer.${ext}`);
  // No Content-Type header: the browser sets the multipart boundary itself.
  const res = await authFetch("/api/transcribe", { method: "POST", headers: assemblyAiHeaders(), body: formData });
  return readJsonOrThrow<TranscriptionResponse>(res);
}
