export type Status = "shipped" | "building" | "planned";

export const statusMeta: Record<Status, { label: string; dot: string; text: string }> = {
  shipped: { label: "SHIPPED", dot: "bg-accent", text: "text-accent" },
  building: { label: "IN PROGRESS", dot: "bg-amber-400", text: "text-amber-400" },
  planned: { label: "PLANNED", dot: "bg-faint", text: "text-faint" },
};

export interface Persona {
  id: string;
  name: string;
  archetype: string;
  mood: string;
  moodColor: string;
  bio: string;
  sample: string;
}

/**
 * Canonical archetype library, matching the UI/UX design doc exactly
 * (Section 8). This is the paid-tier vision - v1 free tier ships tone
 * presets only (see lib/casting.ts), not archetype selection.
 */
export const personas: Persona[] = [
  {
    id: "hiring-manager",
    name: "The Hiring Manager",
    archetype: "Warm, curious",
    mood: "Warm-up",
    moodColor: "#4ade80",
    bio: "Motivation, culture fit, behavioral. The first-round interviewer who wants to see if you'd actually enjoy the role.",
    sample: "What made you want to leave your last team, really?",
  },
  {
    id: "tech-lead",
    name: "The Tech Lead",
    archetype: "Direct, terse",
    mood: "Technical",
    moodColor: "#5b8def",
    bio: "Deep technical drill-down, relentless follow-ups. System design and coding rounds live here.",
    sample: "Walk me through the actual query plan — not what you think happened.",
  },
  {
    id: "skeptic",
    name: "The Skeptic",
    archetype: "Cool, pushes back",
    mood: "Pressing",
    moodColor: "#f87171",
    bio: "Stress-tests your reasoning. Senior and staff-level pressure practice — if there's a soft spot, she's already found it.",
    sample: "But what if that dependency wasn't available — what then?",
  },
  {
    id: "bar-raiser",
    name: "The Bar Raiser",
    archetype: "Neutral, exacting",
    mood: "Calibrating",
    moodColor: "#c084fc",
    bio: "Holistic, calibration-style evaluation. The final-round simulation — nothing flashy, just exacting.",
    sample: "Compare that decision to the alternative you didn't take.",
  },
  {
    id: "peer",
    name: "The Peer",
    archetype: "Casual, collaborative",
    mood: "Collaborative",
    moodColor: "#fbbf24",
    bio: "Think-out-loud, pair-style. Built for collaborative problem solving, not interrogation.",
    sample: "Okay, thinking out loud with you here — what's your first instinct?",
  },
];

export interface Chapter {
  num: string;
  status: Status;
  eyebrow: string;
  title: string;
  body: string;
  subnav: string[];
}

export const chapters: Chapter[] = [
  {
    num: "1.0",
    status: "planned",
    eyebrow: "Meet the panel",
    title: "Four interviewers. Four ways to get caught off guard.",
    body: "Every real interview panel has range — the friendly one, the skeptic, the one who won't let a vague answer slide. PrepPanel is being built to run sessions the same way, so 'good enough' for one persona doesn't sail past another.",
    subnav: ["1.1 Personas", "1.2 Mood shifts", "1.3 Interjections"],
  },
  {
    num: "2.0",
    status: "shipped",
    eyebrow: "Practice",
    title: "Questions that know what you already said.",
    body: "Live today: every question is generated fresh by an LLM, aware of everything already asked in the session, tailored to your role. Mid-answer interjections — the panel jumping in while you're still talking — are the next layer on this same pipeline.",
    subnav: ["2.1 Adaptive questions", "2.2 Interjections — building", "2.3 Fallback providers"],
  },
  {
    num: "3.0",
    status: "shipped",
    eyebrow: "Score",
    title: "Feedback with receipts, not a participation trophy.",
    body: "An LLM judge scores content and structure against a defined rubric, annotates specific moments in your answer, and gives feedback you could actually act on before your next interview.",
    subnav: ["3.1 Rubric scoring", "3.2 Inline annotations", "3.3 Retry on bad output"],
  },
  {
    num: "4.0",
    status: "planned",
    eyebrow: "Research",
    title: "Grounded in the company you're actually interviewing with.",
    body: "PrepPanel will read a company's careers pages and engineering blog — respecting robots.txt — and generate questions rooted in what that specific team is actually building, not generic prompts that could apply anywhere.",
    subnav: ["4.1 Careers page scan", "4.2 Eng blog summarization", "4.3 Grounded questions"],
  },
  {
    num: "5.0",
    status: "planned",
    eyebrow: "Progress",
    title: "See the pattern, not just the last score.",
    body: "A dashboard tracking score trends across sessions, weak spots by category, and a weekly recap so you know exactly what to drill before the next one.",
    subnav: ["5.1 Trend charts", "5.2 Weak-spot radar", "5.3 Weekly recap"],
  },
];

export const roadmap = [
  { status: "shipped" as Status, title: "Auth & interview core", body: "JWT auth, Postgres schema, full session loop.", date: "WEEK 1–2" },
  { status: "shipped" as Status, title: "LLM-generated questions", body: "Groq-primary, Gemini-fallback, tailored per role.", date: "WEEK 3" },
  { status: "shipped" as Status, title: "Scoring pipeline", body: "LLM-as-judge rubric scoring, validated and retried.", date: "WEEK 3" },
  { status: "planned" as Status, title: "Interviewer personas", body: "Four distinct panelists, mid-answer interjections.", date: "WEEK 4–5" },
  { status: "planned" as Status, title: "Company research", body: "Careers-page + eng-blog grounded questions.", date: "WEEK 5–6" },
  { status: "planned" as Status, title: "Voice mode", body: "Speak your answers, with delivery metrics.", date: "WEEK 7+" },
];

export const roles = [
  "Backend Engineer", "Product Manager", "Data Scientist", "Frontend Engineer",
  "DevOps Engineer", "Engineering Manager", "ML Engineer", "Designer",
  "Full-Stack Engineer", "Site Reliability Engineer",
];

export const stack = [
  "Groq", "Gemini", "Spring Boot", "PostgreSQL", "Next.js", "GSAP", "JWT", "Flyway",
];
