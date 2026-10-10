import type { StreamWord, StreamingToken } from "@/lib/voice";

/**
 * Live transcription over AssemblyAI's streaming WebSocket.
 *
 * Mic audio -> AudioWorklet (downsample to 16 kHz, 16-bit PCM, 50 ms chunks) -> WebSocket.
 * AssemblyAI answers with "Turn" messages: the text of the current turn keeps updating while you speak
 * (the live captions), and a turn is marked end_of_turn when it decides you've finished a sentence/thought.
 * The microphone stream itself is owned by the caller and is NOT stopped here.
 */

const TARGET_RATE = 16000;
const CONNECT_TIMEOUT_MS = 8000;
const FLUSH_TIMEOUT_MS = 1500;

const WORKLET_SOURCE = `
class PcmDownsampler extends AudioWorkletProcessor {
  constructor() {
    super();
    this.ratio = sampleRate / ${TARGET_RATE};
    this.t = 0;
    this.out = new Int16Array(${TARGET_RATE / 20}); // 50 ms
    this.n = 0;
  }
  process(inputs) {
    const ch = inputs[0] && inputs[0][0];
    if (!ch) return true;
    let t = this.t;
    while (t < ch.length - 1) {
      const i = Math.floor(t);
      const f = t - i;
      let s = ch[i] * (1 - f) + ch[i + 1] * f;
      s = Math.max(-1, Math.min(1, s));
      this.out[this.n++] = s < 0 ? s * 32768 : s * 32767;
      if (this.n === this.out.length) {
        const copy = this.out.slice();
        this.port.postMessage(copy.buffer, [copy.buffer]);
        this.n = 0;
      }
      t += this.ratio;
    }
    this.t = Math.max(0, t - ch.length);
    return true;
  }
}
registerProcessor("pcm-downsampler", PcmDownsampler);
`;

interface TurnState {
  text: string;
  words: StreamWord[];
  ended: boolean;
  formatted: boolean;
}

export interface FinalTranscript {
  text: string;
  words: StreamWord[];
}

export interface Transcriber {
  /** Volume meter for the caller's silence detection. */
  analyser: AnalyserNode;
  /** Stops sending audio, asks AssemblyAI to flush the last words, and returns the full transcript. */
  finish: () => Promise<FinalTranscript>;
  /** Tears everything down without a result. */
  cancel: () => void;
}

export interface TranscriberOptions {
  stream: MediaStream;
  token: StreamingToken;
  /** finalized = completed turns; partial = the sentence still being spoken. */
  onUpdate: (finalized: string, partial: string) => void;
  /** Called if the connection drops while listening. */
  onClosed: () => void;
}

export async function startTranscriber(opts: TranscriberOptions): Promise<Transcriber> {
  const { stream, token } = opts;

  const params = new URLSearchParams({
    sample_rate: String(TARGET_RATE),
    encoding: "pcm_s16le",
    format_turns: "true",
    token: token.token,
  });
  if (token.speechModel) params.set("speech_model", token.speechModel);

  const turns = new Map<number, TurnState>();
  let closedByUs = false;

  const sortedTurns = () => [...turns.entries()].sort((a, b) => a[0] - b[0]).map(([, t]) => t);
  const emit = () => {
    const all = sortedTurns();
    const finalized = all.filter((t) => t.ended).map((t) => t.text).filter(Boolean).join(" ");
    const partial = all.filter((t) => !t.ended).map((t) => t.text).filter(Boolean).join(" ");
    opts.onUpdate(finalized, partial);
  };

  // --- WebSocket ---------------------------------------------------------------
  const ws = new WebSocket(`${token.websocketUrl}?${params.toString()}`);
  ws.binaryType = "arraybuffer";

  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timeout")), CONNECT_TIMEOUT_MS);
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data as string);
        if (msg.type === "Begin") {
          clearTimeout(timer);
          resolve();
        } else if (msg.error) {
          clearTimeout(timer);
          reject(new Error(String(msg.error)));
        }
      } catch {
        /* ignore */
      }
    };
    ws.onerror = () => {
      clearTimeout(timer);
      reject(new Error("connection failed"));
    };
    ws.onclose = () => {
      clearTimeout(timer);
      reject(new Error("closed before start"));
    };
  }).catch((e) => {
    closedByUs = true;
    try {
      ws.close();
    } catch {
      /* ignore */
    }
    throw e;
  });

  // Connected: from here on, handle turns and unexpected drops.
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data as string);
      if (msg.type !== "Turn") return;
      const order: number = typeof msg.turn_order === "number" ? msg.turn_order : 0;
      const prev = turns.get(order);
      if (prev?.ended && !msg.end_of_turn) return; // stale partial after the turn closed
      const incoming: StreamWord[] = Array.isArray(msg.words)
        ? msg.words.map((w: { text: string; start: number; end: number }) => ({
            text: String(w.text),
            startMs: Number(w.start) || 0,
            endMs: Number(w.end) || 0,
          }))
        : [];
      const text: string = typeof msg.transcript === "string" ? msg.transcript : "";
      turns.set(order, {
        text: text || (msg.end_of_turn ? prev?.text ?? "" : ""),
        words: incoming.length ? incoming : prev?.words ?? [],
        ended: !!msg.end_of_turn,
        formatted: msg.turn_is_formatted === true,
      });
      emit();
    } catch {
      /* ignore malformed frames */
    }
  };
  ws.onerror = () => undefined;
  ws.onclose = () => {
    if (!closedByUs) opts.onClosed();
  };

  // --- Audio -------------------------------------------------------------------
  const Ctx =
    window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  const audioCtx = new Ctx!();
  let node: AudioWorkletNode | null = null;
  let source: MediaStreamAudioSourceNode | null = null;
  let analyser: AnalyserNode;

  const teardownAudio = () => {
    try {
      if (node) node.port.onmessage = null;
      node?.disconnect();
      source?.disconnect();
    } catch {
      /* ignore */
    }
    audioCtx.close().catch(() => undefined);
  };

  try {
    await audioCtx.resume();
    const url = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: "application/javascript" }));
    try {
      await audioCtx.audioWorklet.addModule(url);
    } finally {
      URL.revokeObjectURL(url);
    }
    source = audioCtx.createMediaStreamSource(stream);
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 1024;
    node = new AudioWorkletNode(audioCtx, "pcm-downsampler");
    node.port.onmessage = (ev: MessageEvent<ArrayBuffer>) => {
      if (ws.readyState === WebSocket.OPEN) ws.send(ev.data);
    };
    // The worklet is routed through a muted gain node so every browser keeps pulling audio through it.
    const mute = audioCtx.createGain();
    mute.gain.value = 0;
    source.connect(analyser);
    source.connect(node);
    node.connect(mute);
    mute.connect(audioCtx.destination);
  } catch (e) {
    closedByUs = true;
    teardownAudio();
    try {
      ws.close();
    } catch {
      /* ignore */
    }
    throw e;
  }

  // --- Public handle -----------------------------------------------------------
  let done = false;

  const snapshot = (): FinalTranscript => {
    const all = sortedTurns();
    return {
      text: all.map((t) => t.text).filter(Boolean).join(" ").trim(),
      words: all.flatMap((t) => t.words),
    };
  };

  const settled = () => {
    const all = sortedTurns();
    if (all.length === 0) return true;
    const last = all[all.length - 1];
    return all.every((t) => t.ended) && last.formatted;
  };

  return {
    analyser,
    cancel() {
      if (done) return;
      done = true;
      closedByUs = true;
      teardownAudio();
      try {
        ws.close();
      } catch {
        /* ignore */
      }
    },
    async finish() {
      if (done) return snapshot();
      done = true;
      // Stop feeding audio, then ask the server to close out the sentence in progress.
      if (node) node.port.onmessage = null;
      if (ws.readyState === WebSocket.OPEN) {
        try {
          ws.send(JSON.stringify({ type: "ForceEndpoint" }));
        } catch {
          /* ignore */
        }
      }
      const deadline = Date.now() + FLUSH_TIMEOUT_MS;
      while (!settled() && ws.readyState === WebSocket.OPEN && Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 50));
      }
      closedByUs = true;
      try {
        if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: "Terminate" }));
        ws.close();
      } catch {
        /* ignore */
      }
      teardownAudio();
      return snapshot();
    },
  };
}
