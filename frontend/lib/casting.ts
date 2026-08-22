export type Tone = "friendly" | "neutral" | "direct";

export interface TonePreset {
  id: Tone;
  label: string;
  description: string;
}

/** Legacy single-tone flow - still supported server-side as a fallback when no panel is cast. */
export const TONE_PRESETS: TonePreset[] = [
  { id: "friendly", label: "Friendly", description: "Warm, encouraging. Wants you to succeed." },
  { id: "neutral", label: "Neutral", description: "Even-keeled, professional default." },
  { id: "direct", label: "Direct", description: "No pleasantries. Terse, high standards." },
];

export interface Archetype {
  id: string;
  name: string;
  tone: string;
  focus: string;
  bestFor: string;
  color: string;
}

/**
 * Canonical archetype library - IDs must match com.preppanel.panel.PanelistArchetype
 * on the backend exactly, since archetypeId is sent verbatim in PanelistSelection.
 */
export const ARCHETYPES: Archetype[] = [
  { id: "hiring-manager", name: "The Hiring Manager", tone: "Warm, curious", focus: "Motivation, culture fit, behavioral", bestFor: "Warm-up / first round", color: "#4ade80" },
  { id: "tech-lead", name: "The Tech Lead", tone: "Direct, terse", focus: "Deep technical drill-down, follow-ups", bestFor: "System design, coding rounds", color: "#5b8def" },
  { id: "skeptic", name: "The Skeptic", tone: "Cool, pushes back", focus: "Stress-tests reasoning", bestFor: "Senior/staff pressure practice", color: "#f87171" },
  { id: "bar-raiser", name: "The Bar Raiser", tone: "Neutral, exacting", focus: "Holistic, calibration-style", bestFor: "Final-round simulation", color: "#c084fc" },
  { id: "peer", name: "The Peer", tone: "Casual, collaborative", focus: "Think-out-loud, pair-style", bestFor: "Collaborative problem solving", color: "#fbbf24" },
];

export function getArchetype(id: string | null | undefined): Archetype {
  return ARCHETYPES.find((a) => a.id === id) ?? ARCHETYPES[0];
}

export const MAX_PANELISTS = 4;

export interface ExperienceLevelOption {
  id: string;
  label: string;
}

export const EXPERIENCE_LEVELS: ExperienceLevelOption[] = [
  { id: "junior", label: "Junior" },
  { id: "mid", label: "Mid-level" },
  { id: "senior", label: "Senior" },
  { id: "staff", label: "Staff+" },
];
