"use client";

import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { ApiError } from "@/lib/api";
import { transcribeAudio, type AudioMetrics } from "@/lib/voice";

const MAX_SECONDS = 180; // hard cap on one answer
const GIVE_UP_IF_SILENT_SECONDS = 30; // mic opened but nobody spoke
const POLL_MS = 100;
const CALIBRATION_MS = 600;

type RecState = "idle" | "recording" | "transcribing";
type StopReason = "silence" | "manual" | "max" | "nospeech";

function pickMimeType(): string | undefined {
  if (typeof MediaRecorder === "undefined") return undefined;
  return ["audio/webm;codecs=opus", "audio/webm", "audio/mp4"].find((t) => MediaRecorder.isTypeSupported(t));
}

function rmsOf(buffer: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < buffer.length; i++) sum += buffer[i] * buffer[i];
  return Math.sqrt(sum / buffer.length);
}

/**
 * Record -> backend -> AssemblyAI -> transcript.
 *
 * With autoSend on, it listens for you to start talking, then waits for `silenceSeconds` of quiet
 * and finishes by itself (a countdown shows; speaking again resets it). The pause detection is a
 * simple volume check in the browser, calibrated against your room's background noise.
 */
export default function VoiceRecorder({
  disabled,
  autoSend,
  silenceSeconds,
  onTranscript,
}: {
  disabled?: boolean;
  autoSend: boolean;
  silenceSeconds: number;
  /** autoSend is true when the answer should be submitted immediately. */
  onTranscript: (text: string, metrics: AudioMetrics, autoSend: boolean) => void;
}) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<RecState>("idle");
  const [seconds, setSeconds] = useState(0);
  const [level, setLevel] = useState(0);
  const [heardSpeech, setHeardSpeech] = useState(false);
  const [countdown, setCountdown] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const stopReasonRef = useRef<StopReason>("manual");
  const spokeRef = useRef(false);

  // Latest props, readable from inside the polling interval without restarting it.
  const autoSendRef = useRef(autoSend);
  const silenceRef = useRef(silenceSeconds);
  const onTranscriptRef = useRef(onTranscript);
  autoSendRef.current = autoSend;
  silenceRef.current = silenceSeconds;
  onTranscriptRef.current = onTranscript;

  const supported = typeof window !== "undefined" && !!navigator.mediaDevices?.getUserMedia && !!pickMimeType();

  function cleanup() {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = null;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    audioCtxRef.current?.close().catch(() => undefined);
    audioCtxRef.current = null;
  }

  useEffect(() => cleanup, []);

  function stop(reason: StopReason) {
    if (recorderRef.current?.state === "recording") {
      stopReasonRef.current = reason;
      recorderRef.current.stop();
    }
  }

  async function start() {
    setError(null);
    setHeardSpeech(false);
    setCountdown(null);
    spokeRef.current = false;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true },
      });
      streamRef.current = stream;

      const mimeType = pickMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => handleStopped(recorder);

      // Volume meter for pause detection.
      const Ctx =
        window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      const audioCtx = new Ctx!();
      audioCtxRef.current = audioCtx;
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 1024;
      audioCtx.createMediaStreamSource(stream).connect(analyser);
      const samples = new Float32Array(analyser.fftSize);

      recorderRef.current = recorder;
      recorder.start();
      setSeconds(0);
      setState("recording");

      const startedAt = Date.now();
      let calibrationSum = 0;
      let calibrationCount = 0;
      let threshold = 0.02;
      let loudPolls = 0;
      let lastVoiceAt = startedAt;

      pollRef.current = setInterval(() => {
        const now = Date.now();
        const elapsed = now - startedAt;
        analyser.getFloatTimeDomainData(samples);
        const rms = rmsOf(samples);
        setLevel(Math.min(1, rms / 0.12));
        setSeconds(Math.floor(elapsed / 1000));

        // First ~0.6s: measure the room's background noise to set the "is this speech?" bar.
        if (elapsed < CALIBRATION_MS) {
          calibrationSum += rms;
          calibrationCount++;
          if (elapsed + POLL_MS >= CALIBRATION_MS && calibrationCount > 0) {
            threshold = Math.min(0.06, Math.max(0.015, (calibrationSum / calibrationCount) * 3));
          }
          return;
        }

        const loud = rms > threshold;
        loudPolls = loud ? loudPolls + 1 : 0;
        if (loud) lastVoiceAt = now;

        // Count as "started speaking" only after ~0.2s of sustained sound, so a cough/click doesn't.
        if (!spokeRef.current && loudPolls >= 2) {
          spokeRef.current = true;
          setHeardSpeech(true);
        }

        if (spokeRef.current && autoSendRef.current) {
          const silentMs = now - lastVoiceAt;
          const limitMs = silenceRef.current * 1000;
          if (silentMs >= limitMs) {
            stop("silence");
            return;
          }
          setCountdown(silentMs > 800 ? Math.ceil((limitMs - silentMs) / 1000) : null);
        }

        if (!spokeRef.current && elapsed > GIVE_UP_IF_SILENT_SECONDS * 1000) {
          stop("nospeech");
        } else if (elapsed > MAX_SECONDS * 1000) {
          stop("max");
        }
      }, POLL_MS);
    } catch {
      cleanup();
      setState("idle");
      setError("Microphone access was blocked. Allow it in your browser, or type your answer.");
    }
  }

  async function handleStopped(recorder: MediaRecorder) {
    const reason = stopReasonRef.current;
    const spoke = spokeRef.current;
    cleanup();
    setLevel(0);
    setCountdown(null);

    if (reason === "nospeech" || !spoke) {
      setState("idle");
      setError("I didn't hear anything — check your microphone and try again.");
      return;
    }

    const blob = new Blob(chunksRef.current, { type: recorder.mimeType || "audio/webm" });
    if (blob.size < 1000) {
      setState("idle");
      setError("That recording was empty — try again.");
      return;
    }

    setState("transcribing");
    try {
      const result = await transcribeAudio(authFetch, blob);
      onTranscriptRef.current(result.text, result.audioMetrics, autoSendRef.current);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Transcription failed — you can still type your answer.");
    } finally {
      setState("idle");
    }
  }

  if (!supported) {
    return <p className="mt-3 text-xs text-faint">Voice answers aren&apos;t supported in this browser — type instead.</p>;
  }

  const mm = Math.floor(seconds / 60);
  const ss = String(seconds % 60).padStart(2, "0");

  return (
    <div className="mt-3">
      <div className="flex flex-wrap items-center gap-3">
        {state === "recording" ? (
          <button
            onClick={() => stop("manual")}
            className="rounded-md border border-red-400/50 bg-red-400/10 px-4 py-2 font-mono text-xs text-red-300 transition hover:bg-red-400/20"
          >
            ■ {autoSend ? "Stop & send" : "Stop"} · {mm}:{ss}
          </button>
        ) : (
          <button
            onClick={start}
            disabled={disabled || state === "transcribing"}
            className="rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong disabled:cursor-not-allowed disabled:opacity-60"
          >
            {state === "transcribing" ? (autoSend ? "Transcribing & sending…" : "Transcribing…") : "🎙 Answer by voice"}
          </button>
        )}

        {state === "recording" && (
          <>
            <div className="h-1.5 w-24 overflow-hidden rounded-full bg-line" aria-hidden>
              <div className="h-full bg-accent transition-[width] duration-100" style={{ width: `${level * 100}%` }} />
            </div>
            <span className="text-xs text-muted">
              {!heardSpeech
                ? "Listening… start speaking"
                : countdown !== null
                  ? `Sending in ${countdown}… (keep talking to continue)`
                  : autoSend
                    ? `Speak naturally — I'll send after a ${silenceSeconds}s pause`
                    : "Recording…"}
            </span>
          </>
        )}

        {state === "idle" && (
          <span className="text-xs text-faint">
            {autoSend
              ? `Auto-sends after a ${silenceSeconds}s pause. Change this in Account → API keys.`
              : "The transcript replaces the text box — edit it before submitting."}
          </span>
        )}
      </div>
      {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
    </div>
  );
}
