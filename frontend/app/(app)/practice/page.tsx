"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { readJsonOrThrow, ApiError } from "@/lib/api";
import {
  createSession,
  submitAnswer,
  completeSession,
  scoreSession,
  type SessionResponse,
  type PanelistSelection,
} from "@/lib/interviews";
import { recordSessionStart, markSessionCompleted } from "@/lib/session-store";
import { withMinimumDelay } from "@/lib/timing";
import { ARCHETYPES, EXPERIENCE_LEVELS, MAX_PANELISTS, getArchetype } from "@/lib/casting";
import Orb, { type OrbState } from "@/components/app/Orb";

type Stage = "setup" | "lobby" | "live" | "debrief";

const PREP_KEY = "preppanel_prep_profile";

interface CastSlot {
  archetypeId: string;
  name: string;
}

function moodToOrbState(mood: string | null | undefined): OrbState {
  if (mood === "positive") return "speaking";
  if (mood === "negative") return "listening";
  return "idle";
}

export default function PracticePage() {
  const router = useRouter();
  const { authFetch } = useAuth();

  const [stage, setStage] = useState<Stage>("setup");
  const [role, setRole] = useState("");
  const [experienceLevel, setExperienceLevel] = useState<string | null>(null);
  const [cast, setCast] = useState<CastSlot[]>([]);
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [answerText, setAnswerText] = useState("");
  const [revealingQuestion, setRevealingQuestion] = useState(false);
  const [showStructureHelper, setShowStructureHelper] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [debriefStep, setDebriefStep] = useState(0);
  const [debriefTerminated, setDebriefTerminated] = useState<string | null>(null);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(PREP_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed.targetRole) setRole(parsed.targetRole);
      }
    } catch {
      /* ignore */
    }
  }, []);

  function toggleArchetype(archetypeId: string) {
    setCast((prev) => {
      const exists = prev.find((c) => c.archetypeId === archetypeId);
      if (exists) return prev.filter((c) => c.archetypeId !== archetypeId);
      if (prev.length >= MAX_PANELISTS) return prev;
      return [...prev, { archetypeId, name: getArchetype(archetypeId).name }];
    });
  }

  function renameSlot(archetypeId: string, name: string) {
    setCast((prev) => prev.map((c) => (c.archetypeId === archetypeId ? { ...c, name } : c)));
  }

  async function handleStart() {
    setBusy(true);
    setError(null);
    try {
      const panelists: PanelistSelection[] = cast.map((c) => ({ archetypeId: c.archetypeId, name: c.name }));
      const res = await createSession(authFetch, { role, panelists, experienceLevel: experienceLevel ?? undefined });
      const data = await readJsonOrThrow<SessionResponse>(res);
      setSession(data);
      recordSessionStart(data.sessionId, data.role, data.panelists.map((p) => p.name));
      setStage("lobby");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start a session.");
    } finally {
      setBusy(false);
    }
  }

  function enterRoom() {
    setStage("live");
    setRevealingQuestion(true);
    setTimeout(() => setRevealingQuestion(false), 700);
  }

  async function handleAnswer() {
    if (!session) return;
    setBusy(true);
    setError(null);
    setRevealingQuestion(true);
    try {
      const res = await withMinimumDelay(submitAnswer(authFetch, session.sessionId, answerText), 700);
      const data = await readJsonOrThrow<SessionResponse>(res);
      setSession(data);
      setAnswerText("");
      setShowStructureHelper(false);

      if (data.completed) {
        markSessionCompleted(session.sessionId);
        runDebrief(session.sessionId, data.terminatedByPanel ? data.terminationReason : null);
      } else {
        setRevealingQuestion(false);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not submit your answer.");
      setRevealingQuestion(false);
    } finally {
      setBusy(false);
    }
  }

  async function handleEndEarly() {
    if (!session) return;
    setBusy(true);
    setError(null);
    try {
      await completeSession(authFetch, session.sessionId);
      markSessionCompleted(session.sessionId);
      runDebrief(session.sessionId, null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not end the session.");
      setBusy(false);
    }
  }

  function runDebrief(sessionId: string, terminationReason: string | null) {
    setStage("debrief");
    setDebriefStep(0);
    setDebriefTerminated(terminationReason);

    const stepTimer1 = setTimeout(() => setDebriefStep(1), 700);
    const stepTimer2 = setTimeout(() => setDebriefStep(2), 1500);

    withMinimumDelay(scoreSession(authFetch, sessionId), 2400)
      .then(() => {
        clearTimeout(stepTimer1);
        clearTimeout(stepTimer2);
        router.push(`/growth/${sessionId}`);
      })
      .catch(() => {
        router.push(`/growth/${sessionId}`);
      });
  }

  // ---------------------------------------------------------------- setup

  if (stage === "setup") {
    return (
      <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
        <span className="fig-label">CASTING YOUR PANEL</span>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">
          Who's in the room today?
        </h1>
        <p className="mt-2 text-sm text-muted">
          Pick 1–{MAX_PANELISTS} panelists. They'll take turns, react to what
          you actually say, and can end the interview if things go badly off
          the rails.
        </p>

        <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6">
          <label className="fig-label block text-faint">ROLE</label>
          <input
            value={role}
            onChange={(e) => setRole(e.target.value)}
            placeholder="e.g. Backend Engineer"
            className="mt-2 w-full rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-1 focus:ring-accent"
          />

          <div className="mt-6">
            <label className="fig-label block text-faint">MODE</label>
            <div className="mt-2 grid grid-cols-3 gap-3">
              <div className="rounded-lg border border-accent bg-accent/10 px-3 py-3 text-center">
                <span className="text-sm text-ink">Text</span>
              </div>
              {["Voice", "Panel call"].map((m) => (
                <div
                  key={m}
                  className="rounded-lg border border-line px-3 py-3 text-center opacity-40"
                  title="Coming soon"
                >
                  <span className="text-sm text-muted">{m}</span>
                  <span className="mt-0.5 block font-mono text-[10px] text-faint">soon</span>
                </div>
              ))}
            </div>
          </div>

          <div className="mt-6">
            <label className="fig-label block text-faint">
              PANELISTS ({cast.length}/{MAX_PANELISTS})
            </label>
            <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
              {ARCHETYPES.map((a) => {
                const slot = cast.find((c) => c.archetypeId === a.id);
                const selected = !!slot;
                return (
                  <div
                    key={a.id}
                    className={`rounded-lg border p-4 transition-all duration-150 ${
                      selected
                        ? "border-lineStrong bg-canvas"
                        : "border-line bg-canvas/40 hover:border-lineStrong"
                    }`}
                  >
                    <button
                      onClick={() => toggleArchetype(a.id)}
                      className="flex w-full items-center gap-3 text-left"
                    >
                      <Orb state={selected ? "idle" : "idle"} size={22} color={a.color} />
                      <div className="flex-1">
                        <span className="text-sm font-medium text-ink">{a.name}</span>
                        <p className="mt-0.5 font-mono text-[10px] text-faint">{a.tone}</p>
                      </div>
                      <span
                        className={`h-4 w-4 flex-none rounded border ${
                          selected ? "border-accent bg-accent" : "border-line"
                        }`}
                      />
                    </button>
                    {selected && (
                      <input
                        value={slot!.name}
                        onChange={(e) => renameSlot(a.id, e.target.value)}
                        placeholder={a.name}
                        className="mt-3 w-full rounded-md border border-line bg-canvas px-3 py-2 text-xs text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-1 focus:ring-accent"
                      />
                    )}
                    <p className="mt-2 text-xs leading-relaxed text-faint">{a.focus}</p>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="mt-6">
            <label className="fig-label block text-faint">YOUR TARGET LEVEL (OPTIONAL)</label>
            <div className="mt-2 grid grid-cols-4 gap-2">
              {EXPERIENCE_LEVELS.map((lvl) => (
                <button
                  key={lvl.id}
                  onClick={() => setExperienceLevel(experienceLevel === lvl.id ? null : lvl.id)}
                  className={`rounded-lg border px-2 py-2 text-center text-xs transition ${
                    experienceLevel === lvl.id
                      ? "border-accent bg-accent/10 text-ink"
                      : "border-line text-muted hover:border-lineStrong"
                  }`}
                >
                  {lvl.label}
                </button>
              ))}
            </div>
            <p className="mt-2 font-mono text-[11px] text-faint">
              Calibrates question difficulty. Couldn't extract this from a
              LinkedIn profile reliably (LinkedIn blocks scraping) — this is
              the honest alternative.
            </p>
          </div>

          {error && (
            <div className="mt-5 rounded-lg border border-red-400/30 bg-red-400/10 px-4 py-3 text-sm text-red-300">
              {error}
            </div>
          )}

          <button
            onClick={handleStart}
            disabled={busy || !role.trim() || cast.length === 0}
            className="mt-6 w-full rounded-md bg-ink px-6 py-3 font-mono text-sm font-medium text-canvas transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-60"
          >
            {busy ? "Setting up…" : cast.length === 0 ? "Pick at least one panelist" : "Enter the room →"}
          </button>
        </div>
      </div>
    );
  }

  // ---------------------------------------------------------------- lobby

  if (stage === "lobby") {
    return (
      <div className="mx-auto flex min-h-[80vh] max-w-md flex-col items-center justify-center px-6 text-center">
        <div className="flex gap-3">
          {cast.map((c) => (
            <Orb key={c.archetypeId} state="idle" size={48} color={getArchetype(c.archetypeId).color} />
          ))}
        </div>
        <span className="fig-label mt-6">LOBBY</span>
        <h1 className="mt-2 text-2xl font-semibold text-ink">Take a breath.</h1>
        <p className="mt-3 text-sm leading-relaxed text-muted">
          {cast.map((c) => c.name).join(", ")} — focused on {role || "your role"}.
          Answer in your own words. There's no perfect script here.
        </p>
        <button
          onClick={enterRoom}
          className="mt-8 rounded-md bg-ink px-6 py-3 font-mono text-sm font-medium text-canvas transition hover:bg-white"
        >
          Enter
        </button>
      </div>
    );
  }

  // ---------------------------------------------------------------- live

  if (stage === "live" && session) {
    const q = session.currentQuestion;
    const speakerColor = getArchetype(q?.panelistArchetypeId).color;

    return (
      <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
        <div className="flex items-center justify-between">
          <span className="fig-label">
            {q ? `Q${q.sequenceNum} OF 5` : ""} · {session.role.toUpperCase()}
          </span>
          <button
            onClick={handleEndEarly}
            disabled={busy}
            className="font-mono text-[11px] text-faint transition hover:text-muted"
          >
            End early
          </button>
        </div>

        <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6 sm:p-8">
          <div className="flex items-start gap-4">
            <Orb
              state={revealingQuestion ? "thinking" : moodToOrbState(q?.moodShift)}
              size={40}
              color={speakerColor}
            />
            <div className="flex-1">
              {revealingQuestion ? (
                <p className="text-sm text-faint">thinking…</p>
              ) : (
                <>
                  {q?.panelistName && (
                    <span className="text-xs font-medium" style={{ color: speakerColor }}>
                      {q.panelistName}
                    </span>
                  )}
                  {q?.reactionText && (
                    <p className="mt-1 text-sm italic text-muted">"{q.reactionText}"</p>
                  )}
                  <p className="mt-2 text-lg leading-relaxed text-ink">{q?.questionText}</p>
                </>
              )}
            </div>
          </div>

          {!revealingQuestion && (
            <>
              <button
                onClick={() => setShowStructureHelper((v) => !v)}
                className="mt-5 font-mono text-[11px] text-faint transition hover:text-muted"
              >
                {showStructureHelper ? "Hide" : "Show"} structure helper
              </button>
              {showStructureHelper && (
                <p className="mt-2 rounded-lg border border-line bg-canvas/60 px-4 py-3 text-xs leading-relaxed text-muted">
                  <strong className="text-ink">STAR:</strong> Situation — what was
                  the context? Task — what were you responsible for? Action —
                  what did you actually do? Result — what happened, ideally
                  with a number.
                </p>
              )}

              <textarea
                value={answerText}
                onChange={(e) => setAnswerText(e.target.value)}
                placeholder="Type your answer…"
                rows={5}
                className="mt-5 w-full resize-none rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-1 focus:ring-accent"
              />

              {error && (
                <div className="mt-4 rounded-lg border border-red-400/30 bg-red-400/10 px-4 py-3 text-sm text-red-300">
                  {error}
                </div>
              )}

              <button
                onClick={handleAnswer}
                disabled={busy || !answerText.trim()}
                className="mt-4 rounded-md bg-ink px-6 py-3 font-mono text-sm font-medium text-canvas transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-60"
              >
                {busy ? "Submitting…" : "Submit answer"}
              </button>
            </>
          )}
        </div>
      </div>
    );
  }

  // ---------------------------------------------------------------- debrief

  if (stage === "debrief") {
    const lines = debriefTerminated
      ? [`The panel ended this session early.`, debriefTerminated, "Scoring what you answered…"]
      : ["Reviewing your answers…", "Scoring structure…", "Compiling feedback…"];

    return (
      <div className="mx-auto flex min-h-[80vh] max-w-md flex-col items-center justify-center px-6 text-center">
        <Orb state="thinking" size={64} color={debriefTerminated ? "#f87171" : "#5b8def"} />
        <span className="fig-label mt-6">DEBRIEF</span>
        <div className="mt-4 flex flex-col gap-2">
          {lines.slice(0, debriefStep + 1).map((line, i) => (
            <p key={i} className="text-sm text-muted">
              {line}
            </p>
          ))}
        </div>
      </div>
    );
  }

  return null;
}
