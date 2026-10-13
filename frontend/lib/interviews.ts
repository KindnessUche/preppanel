export interface PanelistSelection {
  archetypeId: string;
  name: string;
}

export interface QuestionResponse {
  questionId: string;
  sequenceNum: number;
  questionText: string;
  questionType: string;
  panelistArchetypeId: string | null;
  panelistName: string | null;
  reactionText: string | null; // reaction to the PREVIOUS answer; "" for the first question
  moodShift: string | null; // "positive" | "neutral" | "negative"
  /** What kind of move the panel is making: the opening, a new topic, a follow-up, a challenge, or the close. */
  turnType: TurnType | null;
}

export type TurnType = "opening" | "new_topic" | "probe" | "challenge" | "redirect" | "wrap_up";

/** Short label for the question header, e.g. "FOLLOW-UP". Empty for ordinary topic questions. */
export function turnTypeLabel(t: TurnType | string | null | undefined): string {
  switch (t) {
    case "opening":
      return "OPENING";
    case "probe":
      return "FOLLOW-UP";
    case "challenge":
      return "PUSHING BACK";
    case "redirect":
      return "BACK TO THE QUESTION";
    case "wrap_up":
      return "CLOSING";
    default:
      return "";
  }
}

export interface SessionResponse {
  sessionId: string;
  role: string;
  mode: string;
  tone: string;
  panelists: PanelistSelection[];
  status: string;
  currentQuestion: QuestionResponse | null;
  completed: boolean;
  terminatedByPanel: boolean;
  terminationReason: string | null;
}

export interface ScoreResponse {
  contentScore: number;
  structureScore: number;
  technicalScore: number | null;
  deliveryScore: number | null;
  feedbackText: string;
  detail?: ScoreDetail | null;
}

export interface ScoreDetail {
  onTopic?: boolean;
  driftNote?: string;
  depth?: "shallow" | "adequate" | "deep";
  structureNotes?: string;
  accuracyIssues?: string[];
  strengths?: string[];
  improvements?: string[];
  modelAnswer?: string;
}

export interface SessionReportResponse {
  sessionId: string;
  role: string;
  tone: string;
  status: string;
  averageContentScore: number | null;
  averageStructureScore: number | null;
  items: {
    sequenceNum: number;
    questionText: string;
    panelistName: string | null;
    answerText: string | null;
    score: ScoreResponse | null;
    turnType: TurnType | null;
  }[];
}

import type { AudioMetrics } from "@/lib/voice";
import { llmHeaders } from "@/lib/settings-store";

export type AuthFetch = (path: string, init?: RequestInit) => Promise<Response>;

export interface CreateSessionParams {
  targetContext?: string;
  role: string;
  panelists?: PanelistSelection[]; // 1-4 for a real panel; omit/empty for the legacy single-tone flow
  tone?: string; // only used as a fallback when panelists is empty
  experienceLevel?: string; // "junior" | "mid" | "senior" | "staff"
}

export function createSession(authFetch: AuthFetch, params: CreateSessionParams) {
  return authFetch("/api/interviews", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...llmHeaders() },
    body: JSON.stringify({
      role: params.role,
      mode: "text",
      tone: params.tone ?? "neutral",
      panelists: params.panelists ?? [],
      experienceLevel: params.experienceLevel ?? null,
      targetContext: params.targetContext ?? null,
    }),
  });
}

export function submitAnswer(
  authFetch: AuthFetch,
  sessionId: string,
  answerText: string,
  audioMetrics?: AudioMetrics | null
) {
  return authFetch(`/api/interviews/${sessionId}/answers`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...llmHeaders() },
    body: JSON.stringify({ answerText, audioMetrics: audioMetrics ?? null }),
  });
}

export function completeSession(authFetch: AuthFetch, sessionId: string) {
  return authFetch(`/api/interviews/${sessionId}/complete`, { method: "POST", headers: llmHeaders() });
}

export function scoreSession(authFetch: AuthFetch, sessionId: string) {
  return authFetch(`/api/interviews/${sessionId}/score`, { method: "POST", headers: llmHeaders() });
}

export function getSessionReport(authFetch: AuthFetch, sessionId: string) {
  return authFetch(`/api/interviews/${sessionId}/report`, { method: "GET" });
}
