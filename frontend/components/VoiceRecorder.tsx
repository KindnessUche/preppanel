"use client";

import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { ApiError } from "@/lib/api";
import { computeMetrics, fetchStreamingToken, type AudioMetrics } from "@/lib/voice";
import { startTranscriber, type Transcriber } from "@/lib/streaming";
import type { MicHandle } from "@/lib/microphone";

const MAX_SECONDS = 180; // hard cap on one answer
const NO_SPEECH_GIVE_UP_SECONDS = 60; // listening, but nobody has spoken (reading/thinking is normal)
const POLL_MS = 100;
const CALIBRATION_MS = 600;

type RecState = "idle" | "connecting" | "listening" | "finishing";
type StopReason = "silence" | "manual" | "edit" | "max" | "nospeech" | "dropped";

function rmsOf(buffer: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < buffer.length; i++) sum += buffer[i] * buffer[i];
  return Math.sqrt(sum / buffer.length);
}

/**
 * Hands-free voice answers with live captions.
 *
 * Mount it when a question appears: with `autoStart` it starts listening right away (no click). Words show
 * up as you say them. With autoSend on, it finishes and submits after `silenceSeconds` of quiet (countdown
 * shown; speaking again resets it). "Edit instead" during the countdown puts the text in the answer box
 * rather than sending. Typing in the answer box (`suspended`) switches the mic off for this question.
 */
export default function VoiceRecorder({
  mic,
  disabled,
  autoStart,
  suspended,
  autoSend,
  silenceSeconds,
  onTranscript,
  onManualStart,
}: {
  mic: MicHandle;
  disabled?: boolean;
  /** Begin listening as soon as this mounts (i.e. when a new question appears). */
  autoStart: boolean;
  /** True while the person is typing instead; the mic stays off. */
  suspended: boolean;
  autoSend: boolean;
  silenceSeconds: number;
  /** autoSend is true when the answer should be submitted immediately. metrics is null if they couldn't be computed. */
  onTranscript: (text: string, metrics: AudioMetrics | null, autoSend: boolean) => void;
  /** The person pressed the mic button themselves (clears `suspended`). */
  onManualStart: () => void;
}) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<RecState>("idle");
  const [seconds, setSeconds] = useState(0);
  const [level, setLevel] = useState(0);
  const [heardSpeech, setHeardSpeech] = useState(false);
  const [countdown, setCountdown] = useState<number | null>(null);
  const [caption, setCaption] = useState({ finalized: "", partial: "" });
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const transcriberRef = useRef<Transcriber | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const runRef = useRef(0); // bumped on every start/cancel so stale async work can tell it's obsolete
  const stoppingRef = useRef(false);
  const spokeRef = useRef(false);
  const lastVoiceAtRef = useRef(0);
  const startedAtRef = useRef(0);
  const captionLenRef = useRef(0);

  // Latest props/handlers, readable inside intervals and callbacks without re-creating them.
  const autoSendRef = useRef(autoSend);
  const silenceRef = useRef(silenceSeconds);
  const onTranscriptRef = useRef(onTranscript);
  autoSendRef.current = autoSend;
  silenceRef.current = silenceSeconds;
  onTranscriptRef.current = onTranscript;

  function clearPoll() {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = null;
  }

  /** Abandon whatever is in flight without producing a result. */
  function cancelAll() {
    runRef.current++;
    clearPoll();
    transcriberRef.current?.cancel();
    transcriberRef.current = null;
    stoppingRef.current = false;
    setLevel(0);
    setCountdown(null);
  }

  useEffect(() => {
    if (autoStart && !disabled && !suspended && mic.stream) void start();
    return cancelAll;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Typing in the answer box takes over from the mic.
  useEffect(() => {
    if (suspended) {
      cancelAll();
      setState("idle");
      setCaption({ finalized: "", partial: "" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [suspended]);

  async function start() {
    if (!mic.stream) return;
    const run = ++runRef.current;
    stoppingRef.current = false;
    setError(null);
    setNotice(null);
    setHeardSpeech(false);
    setCountdown(null);
    setCaption({ finalized: "", partial: "" });
    captionLenRef.current = 0;
    spokeRef.current = false;
    setState("connecting");

    try {
      const token = await fetchStreamingToken(authFetch);
      if (run !== runRef.current) return;
      const transcriber = await startTranscriber({
        stream: mic.stream,
        token,
        onUpdate: (finalized, partial) => {
          if (run !== runRef.current) return;
          setCaption({ finalized, partial });
          // New words appearing means speech is happening (works even if the mic is quiet).
          const len = finalized.length + partial.length;
          if (len !== captionLenRef.current) {
            captionLenRef.current = len;
            lastVoiceAtRef.current = Date.now();
            if (len > 0 && !spokeRef.current) {
              spokeRef.current = true;
              setHeardSpeech(true);
            }
          }
        },
        onClosed: () => {
          if (run === runRef.current) void stop("dropped");
        },
      });
      if (run !== runRef.current) {
        transcriber.cancel();
        return;
      }
      transcriberRef.current = transcriber;
      beginPolling(transcriber, run);
    } catch (err) {
      if (run !== runRef.current) return;
      cancelAll();
      setState("idle");
      setError(
        err instanceof ApiError
          ? err.message
          : "Couldn't start live transcription — check your AssemblyAI key, or just type your answer."
      );
    }
  }

  function beginPolling(transcriber: Transcriber, run: number) {
    const samples = new Float32Array(transcriber.analyser.fftSize);
    const startedAt = Date.now();
    startedAtRef.current = startedAt;
    lastVoiceAtRef.current = startedAt;
    setSeconds(0);
    setState("listening");

    let calibrationSum = 0;
    let calibrationCount = 0;
    let threshold = 0.02;
    let loudPolls = 0;

    clearPoll();
    pollRef.current = setInterval(() => {
      if (run !== runRef.current) return;
      const now = Date.now();
      const elapsed = now - startedAt;
      transcriber.analyser.getFloatTimeDomainData(samples);
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
      if (loud) lastVoiceAtRef.current = now;

      // ~0.2s of sustained sound counts as "started speaking", so a cough or click doesn't.
      if (!spokeRef.current && loudPolls >= 2) {
        spokeRef.current = true;
        setHeardSpeech(true);
      }

      if (spokeRef.current && autoSendRef.current) {
        const silentMs = now - lastVoiceAtRef.current;
        const limitMs = silenceRef.current * 1000;
        if (silentMs >= limitMs) {
          void stop("silence");
          return;
        }
        setCountdown(silentMs > 800 ? Math.ceil((limitMs - silentMs) / 1000) : null);
      }

      if (!spokeRef.current && elapsed > NO_SPEECH_GIVE_UP_SECONDS * 1000) {
        void stop("nospeech");
      } else if (elapsed > MAX_SECONDS * 1000) {
        void stop("max");
      }
    }, POLL_MS);
  }

  async function stop(reason: StopReason) {
    const transcriber = transcriberRef.current;
    if (!transcriber || stoppingRef.current) return;
    stoppingRef.current = true;
    const run = runRef.current;
    clearPoll();
    setLevel(0);
    setCountdown(null);

    if (reason === "nospeech") {
      cancelAll();
      setState("idle");
      setNotice("I didn't hear anything. Tap the mic when you're ready, or type your answer.");
      return;
    }

    setState("finishing");
    const durationSeconds = (Date.now() - startedAtRef.current) / 1000;
    const result = await transcriber.finish();
    if (run !== runRef.current) return; // cancelled or unmounted while finishing
    transcriberRef.current = null;
    stoppingRef.current = false;

    const text = result.text.trim();
    if (!text) {
      setState("idle");
      setCaption({ finalized: "", partial: "" });
      setNotice("I didn't catch any words. Tap the mic to try again, or type your answer.");
      return;
    }

    let metrics: AudioMetrics | null = null;
    try {
      metrics = await computeMetrics(authFetch, result.words, durationSeconds);
    } catch {
      /* the answer still goes through; it just won't get a delivery score */
    }
    if (run !== runRef.current) return;

    setState("idle");
    if (reason === "dropped") setNotice("The connection dropped — here's what I caught. Edit it, then submit.");
    onTranscriptRef.current(text, metrics, reason !== "edit" && reason !== "dropped" && autoSendRef.current);
  }

  // ---------------------------------------------------------------- render

  if (mic.status === "unsupported") {
    return <p className="mt-3 text-xs text-faint">Voice answers aren&apos;t supported in this browser — type instead.</p>;
  }

  if (!mic.stream) {
    return (
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button
          onClick={() => void mic.request()}
          disabled={mic.status === "requesting"}
          className="rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong disabled:opacity-60"
        >
          🎙 Enable microphone
        </button>
        <span className="text-xs text-faint">
          {mic.status === "denied"
            ? "Microphone access was blocked. Allow it in your browser's address bar, then tap here."
            : "Allow the microphone to answer by voice."}
        </span>
      </div>
    );
  }

  const active = state !== "idle";
  const mm = Math.floor(seconds / 60);
  const ss = String(seconds % 60).padStart(2, "0");
  const showCaptions = active || caption.finalized || caption.partial;

  return (
    <div className="mt-3">
      <div className="flex flex-wrap items-center gap-3">
        {state === "listening" || state === "finishing" ? (
          <button
            onClick={() => void stop("manual")}
            disabled={state === "finishing"}
            className="rounded-md border border-red-400/50 bg-red-400/10 px-4 py-2 font-mono text-xs text-red-300 transition hover:bg-red-400/20 disabled:opacity-60"
          >
            {state === "finishing" ? "Finishing…" : `■ ${autoSend ? "Stop & send" : "Stop"} · ${mm}:${ss}`}
          </button>
        ) : (
          <button
            onClick={() => {
              onManualStart();
              void start();
            }}
            disabled={disabled || state === "connecting"}
            className="rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong disabled:cursor-not-allowed disabled:opacity-60"
          >
            {state === "connecting" ? "Connecting…" : "🎙 Answer by voice"}
          </button>
        )}

        {state === "listening" && (
          <>
            <div className="h-1.5 w-24 overflow-hidden rounded-full bg-line" aria-hidden>
              <div className="h-full bg-accent transition-[width] duration-100" style={{ width: `${level * 100}%` }} />
            </div>
            <span className="text-xs text-muted">
              {!heardSpeech
                ? "Listening… just start talking"
                : countdown !== null
                  ? `Sending in ${countdown}… (keep talking to continue)`
                  : autoSend
                    ? `Speak naturally — I'll send after a ${silenceSeconds}s pause`
                    : "Listening…"}
            </span>
            {countdown !== null && (
              <button
                onClick={() => void stop("edit")}
                className="font-mono text-[11px] text-faint underline transition hover:text-ink"
              >
                Edit instead
              </button>
            )}
          </>
        )}

        {state === "idle" && !notice && (
          <span className="text-xs text-faint">
            {autoSend
              ? `Answers send after a ${silenceSeconds}s pause. Change this in Account → API keys.`
              : "The transcript goes in the text box — edit it before submitting."}
          </span>
        )}
      </div>

      {showCaptions && (
        <div className="mt-3 rounded-lg border border-line bg-canvas/60 px-4 py-3" aria-live="polite">
          <span className="fig-label text-faint">LIVE CAPTIONS</span>
          <p className="mt-1 min-h-[1.5rem] text-sm leading-relaxed">
            {caption.finalized && <span className="text-ink">{caption.finalized} </span>}
            {caption.partial && <span className="italic text-muted">{caption.partial}</span>}
            {!caption.finalized && !caption.partial && (
              <span className="text-faint">{state === "connecting" ? "Connecting to the microphone…" : "…"}</span>
            )}
          </p>
        </div>
      )}

      {notice && <p className="mt-2 text-xs text-muted">{notice}</p>}
      {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
    </div>
  );
}
