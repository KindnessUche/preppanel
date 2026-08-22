/**
 * The backend doesn't yet have a "list my sessions" endpoint (only
 * GET /api/interviews/{id} for a single session by ID) - so Home and Growth
 * can't ask the server "what have I done." This is a client-side stopgap:
 * we remember session IDs locally on this device as they're created.
 *
 * This is genuinely a known limitation, not a design choice - the real fix
 * is a GET /api/interviews list endpoint on the backend. Until then, this
 * only knows about sessions created from this browser.
 */

export interface StoredSessionRef {
  sessionId: string;
  role: string;
  panelistNames: string[]; // e.g. ["The Skeptic", "The Tech Lead"] or [] for legacy tone-only sessions
  startedAt: string; // ISO timestamp
  completed: boolean;
}

const KEY = "preppanel_sessions";

function readAll(): StoredSessionRef[] {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as StoredSessionRef[]) : [];
  } catch {
    return [];
  }
}

function writeAll(sessions: StoredSessionRef[]) {
  localStorage.setItem(KEY, JSON.stringify(sessions));
}

export function recordSessionStart(sessionId: string, role: string, panelistNames: string[]) {
  const sessions = readAll();
  sessions.unshift({ sessionId, role, panelistNames, startedAt: new Date().toISOString(), completed: false });
  writeAll(sessions);
}

export function markSessionCompleted(sessionId: string) {
  const sessions = readAll();
  const updated = sessions.map((s) => (s.sessionId === sessionId ? { ...s, completed: true } : s));
  writeAll(updated);
}

export function getAllSessions(): StoredSessionRef[] {
  return readAll();
}

export function getMostRecentIncomplete(): StoredSessionRef | null {
  return readAll().find((s) => !s.completed) ?? null;
}

export function getCompletedInLastNDays(days: number): StoredSessionRef[] {
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  return readAll().filter((s) => s.completed && new Date(s.startedAt).getTime() >= cutoff);
}
