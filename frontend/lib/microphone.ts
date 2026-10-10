"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type MicStatus = "idle" | "requesting" | "ready" | "denied" | "unsupported";

export interface MicHandle {
  status: MicStatus;
  stream: MediaStream | null;
  /** Asks the browser for microphone access (shows the permission prompt the first time). */
  request: () => Promise<boolean>;
  release: () => void;
}

/**
 * Owns one microphone stream for a whole interview. Asking once (when the person enters the room) means
 * the browser prompts a single time, and every question can start listening instantly afterwards.
 * Mic access only works on https:// or http://localhost.
 */
export function useMicrophone(): MicHandle {
  const [status, setStatus] = useState<MicStatus>("idle");
  const [stream, setStream] = useState<MediaStream | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const release = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setStream(null);
    setStatus("idle");
  }, []);

  const request = useCallback(async () => {
    if (streamRef.current?.active) return true;
    if (typeof navigator === "undefined" || !navigator.mediaDevices?.getUserMedia) {
      setStatus("unsupported");
      return false;
    }
    setStatus("requesting");
    try {
      const s = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      streamRef.current = s;
      setStream(s);
      setStatus("ready");
      return true;
    } catch {
      setStatus("denied");
      return false;
    }
  }, []);

  useEffect(() => release, [release]);

  return { status, stream, request, release };
}
