"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { readJsonOrThrow, ApiError } from "@/lib/api";
import { llmHeaders } from "@/lib/settings-store";

const PREP_KEY = "preppanel_prep_profile";
const BRIEF_KEY = "preppanel_prep_brief";
const LEVELS = ["junior", "mid", "senior", "staff"];
const TYPES = ["Recruiter screen", "Behavioral", "Technical", "Panel", "Final round"];

interface Brief {
  summary?: string;
  competencies?: { name: string; whyItMatters: string; likelyQuestion: string }[];
  gaps?: { requirement: string; risk: string; howToHandle: string }[];
  strengths?: string[];
  storyBank?: { story: string; competencies: string[] }[];
  questionsToAsk?: string[];
  companyNotes?: string[];
}

interface Saved {
  role: string;
  level: string;
  company: string;
  interviewType: string;
  jd: string;
  brief: Brief;
  context: string;
}

/** Condensed text every interview turn receives, so the panel knows what this interview is for. */
function buildContext(f: Omit<Saved, "brief" | "context">, b: Brief): string {
  const L = (t: string, xs?: string[]) => (xs?.length ? `${t}:\n- ${xs.join("\n- ")}\n` : "");
  return [
    `Company: ${f.company || "unspecified"}. Interview type: ${f.interviewType}.`,
    b.summary ?? "",
    L("Competencies they will screen for", b.competencies?.map((c) => `${c.name} (e.g. ${c.likelyQuestion})`)),
    L("Weak spots in this candidate versus the role (probe these)", b.gaps?.map((g) => `${g.requirement}: ${g.risk}`)),
    L("Strengths", b.strengths),
    `Job description excerpt:\n${f.jd.slice(0, 2500)}`,
  ].join("\n").slice(0, 5800);
}

const input =
  "mt-2 w-full rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-1 focus:ring-accent";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="border-t border-line pt-5">
      <span className="fig-label">{title}</span>
      <div className="mt-3 space-y-3 text-sm leading-relaxed text-muted">{children}</div>
    </div>
  );
}

export default function PrepPage() {
  const { authFetch } = useAuth();
  const [f, setF] = useState({ role: "", level: "mid", company: "", interviewType: "Behavioral", jd: "" });
  const [saved, setSaved] = useState<Saved | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const s: Saved | null = JSON.parse(localStorage.getItem(BRIEF_KEY) ?? "null");
      if (s) {
        setSaved(s);
        setF({ role: s.role, level: s.level, company: s.company, interviewType: s.interviewType, jd: s.jd });
      } else {
        const p = JSON.parse(localStorage.getItem(PREP_KEY) ?? "null");
        if (p?.targetRole) setF((x) => ({ ...x, role: p.targetRole }));
      }
    } catch {
      /* ignore */
    }
  }, []);

  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setF((x) => ({ ...x, [k]: e.target.value }));

  async function build() {
    setBusy(true);
    setError(null);
    try {
      const res = await authFetch("/api/prep/brief", {
        method: "POST",
        headers: { "Content-Type": "application/json", ...llmHeaders() },
        body: JSON.stringify({ role: f.role, level: f.level, company: f.company, interviewType: f.interviewType, jobDescription: f.jd }),
      });
      const brief = await readJsonOrThrow<Brief>(res);
      const next: Saved = { ...f, brief, context: buildContext(f, brief) };
      setSaved(next);
      localStorage.setItem(BRIEF_KEY, JSON.stringify(next));
      localStorage.setItem(PREP_KEY, JSON.stringify({ targetRole: f.role, experienceLevel: f.level }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not build the brief. Try again.");
    } finally {
      setBusy(false);
    }
  }

  const b = saved?.brief;

  return (
    <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
      <span className="fig-label">PREP</span>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">What are you walking into?</h1>
      <p className="mt-2 text-sm text-muted">
        Paste the job description and the panel will interview you for <em>this</em> role, pressing on where your
        background is weakest against it.
      </p>

      <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="fig-label block text-faint">TARGET ROLE</label>
            <input value={f.role} onChange={set("role")} placeholder="e.g. Backend Engineer" className={input} />
          </div>
          <div>
            <label className="fig-label block text-faint">COMPANY</label>
            <input value={f.company} onChange={set("company")} placeholder="e.g. Stripe" className={input} />
          </div>
          <div>
            <label className="fig-label block text-faint">LEVEL</label>
            <select value={f.level} onChange={set("level")} className={input}>
              {LEVELS.map((l) => <option key={l} value={l}>{l}</option>)}
            </select>
          </div>
          <div>
            <label className="fig-label block text-faint">INTERVIEW TYPE</label>
            <select value={f.interviewType} onChange={set("interviewType")} className={input}>
              {TYPES.map((t) => <option key={t}>{t}</option>)}
            </select>
          </div>
        </div>
        <div className="mt-4">
          <label className="fig-label block text-faint">JOB DESCRIPTION</label>
          <textarea value={f.jd} onChange={set("jd")} rows={8} maxLength={12000} placeholder="Paste the full posting. The more of it, the sharper the questions." className={input + " resize-y"} />
        </div>
        {error && <div className="mt-4 rounded-lg border border-red-400/30 bg-red-400/10 px-4 py-3 text-sm text-red-300">{error}</div>}
        <button
          onClick={build}
          disabled={busy || !f.role.trim() || f.jd.trim().length < 80}
          className="mt-5 rounded-md bg-ink px-6 py-3 font-mono text-sm font-medium text-canvas transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-60"
        >
          {busy ? "Building your brief…" : saved ? "Rebuild brief" : "Build my brief"}
        </button>
        <p className="mt-3 text-xs text-faint">
          Your resume (Account) is used if uploaded. Company notes come from the AI&apos;s general knowledge, not live
          research, so they&apos;re marked &ldquo;Verify&rdquo;.
        </p>
      </div>

      {b && saved && (
        <div className="panel-texture mt-6 space-y-5 rounded-2xl border border-line bg-panel/40 p-6">
          <div className="flex items-center justify-between">
            <span className="fig-label">YOUR BRIEF · {saved.role.toUpperCase()}{saved.company && ` @ ${saved.company.toUpperCase()}`}</span>
            <Link href="/practice" className="rounded-md bg-accent/90 px-4 py-2 font-mono text-xs text-canvas transition hover:bg-accent">
              Practice this interview →
            </Link>
          </div>
          {b.summary && <p className="text-sm leading-relaxed text-ink">{b.summary}</p>}

          {!!b.competencies?.length && (
            <Section title="WHAT THEY'LL SCREEN FOR">
              {b.competencies.map((c, i) => (
                <div key={i}>
                  <p className="text-ink">{c.name}</p>
                  <p>{c.whyItMatters}</p>
                  <p className="italic text-faint">Likely: &ldquo;{c.likelyQuestion}&rdquo;</p>
                </div>
              ))}
            </Section>
          )}
          {!!b.gaps?.length && (
            <Section title="WHERE YOU'LL GET PRESSED">
              {b.gaps.map((g, i) => (
                <div key={i}>
                  <p className="text-ink">{g.requirement}</p>
                  <p>{g.risk}</p>
                  <p className="text-faint">How to handle it: {g.howToHandle}</p>
                </div>
              ))}
            </Section>
          )}
          {!!b.storyBank?.length && (
            <Section title="STORY BANK">
              {b.storyBank.map((s, i) => (
                <p key={i}>
                  <span className="text-ink">{s.story}</span>{" "}
                  <span className="font-mono text-[10px] text-faint">{s.competencies?.join(" · ")}</span>
                </p>
              ))}
            </Section>
          )}
          {!!b.questionsToAsk?.length && (
            <Section title="QUESTIONS TO ASK THEM">
              <ul className="list-disc space-y-1 pl-5">{b.questionsToAsk.map((q, i) => <li key={i}>{q}</li>)}</ul>
            </Section>
          )}
          {!!b.companyNotes?.length && (
            <Section title="COMPANY NOTES (UNVERIFIED)">
              <ul className="list-disc space-y-1 pl-5">{b.companyNotes.map((q, i) => <li key={i}>{q}</li>)}</ul>
            </Section>
          )}
        </div>
      )}
    </div>
  );
}
