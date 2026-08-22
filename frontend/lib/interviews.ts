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
  }[];
}

export type AuthFetch = (path: string, init?: RequestInit) => Promise<Response>;

export interface CreateSessionParams {
  role: string;
  panelists?: PanelistSelection[]; // 1-4 for a real panel; omit/empty for the legacy single-tone flow
  tone?: string; // only used as a fallback when panelists is empty
  experienceLevel?: string; // "junior" | "mid" | "senior" | "staff"
}

export function createSession(authFetch: AuthFetch, params: CreateSessionParams) {
  return authFetch("/api/interviews", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      role: params.role,
      mode: "text",
      tone: params.tone ?? "neutral",
      panelists: params.panelists ?? [],
      experienceLevel: params.experienceLevel ?? null,
    }),
  });
}

export function submitAnswer(authFetch: AuthFetch, sessionId: string, answerText: string) {
  return authFetch(`/api/interviews/${sessionId}/answers`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ answerText }),
  });
}

export function completeSession(authFetch: AuthFetch, sessionId: string) {
  return authFetch(`/api/interviews/${sessionId}/complete`, { method: "POST" });
}

export function scoreSession(authFetch: AuthFetch, sessionId: string) {
  return authFetch(`/api/interviews/${sessionId}/score`, { method: "POST" });
}

export function getSessionReport(authFetch: AuthFetch, sessionId: string) {
  return authFetch(`/api/interviews/${sessionId}/report`, { method: "GET" });
}
