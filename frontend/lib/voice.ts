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

/** Uploads a recording to the backend, which proxies AssemblyAI (the API key never reaches the browser). */
export async function transcribeAudio(authFetch: AuthFetch, audio: Blob): Promise<TranscriptionResponse> {
  const ext = audio.type.includes("mp4") ? "m4a" : "webm";
  const formData = new FormData();
  formData.append("file", audio, `answer.${ext}`);
  // No Content-Type header: the browser sets the multipart boundary itself.
  const res = await authFetch("/api/transcribe", { method: "POST", headers: assemblyAiHeaders(), body: formData });
  return readJsonOrThrow<TranscriptionResponse>(res);
}
