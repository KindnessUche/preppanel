"use client";

import { useEffect, useState } from "react";
import { useInView } from "@/lib/useInView";

const PREP_KEY = "preppanel_prep_profile";

interface PrepProfile {
  targetRole: string;
  experienceLevel: string;
}

function useLocalProfile() {
  const [profile, setProfile] = useState<PrepProfile>({ targetRole: "", experienceLevel: "" });
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(PREP_KEY);
      if (raw) setProfile(JSON.parse(raw));
    } catch {
      /* ignore */
    }
  }, []);

  function save(next: PrepProfile) {
    setProfile(next);
    localStorage.setItem(PREP_KEY, JSON.stringify(next));
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  }

  return { profile, save, saved };
}

function DossierPreview() {
  const { ref, inView } = useInView<HTMLDivElement>(0.3);
  const [started, setStarted] = useState(false);

  const steps = [
    "Researching Stripe...",
    "✓ Found careers page",
    "✓ Found engineering blog (3 recent posts)",
    "… Reading posts",
    "… Summarizing themes",
  ];

  return (
    <div ref={ref} className="panel-texture rounded-2xl border border-line bg-panel/40 p-6">
      <div className="flex items-center justify-between">
        <span className="fig-label">THEM — COMPANY DOSSIER</span>
        <span className="fig-label text-faint">PLANNED</span>
      </div>
      <p className="mt-3 text-sm leading-relaxed text-muted">
        Not live yet — this is a preview of how it'll work once the research
        pipeline is built. Real research will cite its sources, tab by
        theme, and show when it was last refreshed.
      </p>

      <button
        onClick={() => setStarted(true)}
        disabled={started}
        className="mt-4 rounded-md border border-line px-4 py-2 font-mono text-xs text-muted transition hover:border-lineStrong hover:text-ink disabled:opacity-50"
      >
        {started ? "Previewing…" : "Preview the research flow"}
      </button>

      {started && (
        <div className="mt-5 space-y-2 border-t border-line pt-5 font-mono text-[13px]">
          {steps.map((line, i) => (
            <div
              key={i}
              className="text-muted opacity-0"
              style={{ animation: "fadeIn 0.5s ease forwards", animationDelay: `${i * 0.6}s` }}
            >
              {line}
            </div>
          ))}
        </div>
      )}

      <style jsx>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(4px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}

export default function PrepPage() {
  const { profile, save, saved } = useLocalProfile();
  const [role, setRole] = useState(profile.targetRole);
  const [level, setLevel] = useState(profile.experienceLevel);

  useEffect(() => {
    setRole(profile.targetRole);
    setLevel(profile.experienceLevel);
  }, [profile]);

  return (
    <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
      <span className="fig-label">PREP</span>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">
        You, and them.
      </h1>
      <p className="mt-2 text-sm text-muted">
        What you bring to the table, and what you're walking into.
      </p>

      <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6">
        <div className="flex items-center justify-between">
          <span className="fig-label">YOU</span>
          <span className="fig-label text-faint">SAVED ON THIS DEVICE</span>
        </div>

        <div className="mt-4 flex flex-col gap-4">
          <div>
            <label className="fig-label block text-faint">TARGET ROLE</label>
            <input
              value={role}
              onChange={(e) => setRole(e.target.value)}
              placeholder="e.g. Backend Engineer"
              className="mt-2 w-full rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-1 focus:ring-accent"
            />
          </div>
          <div>
            <label className="fig-label block text-faint">EXPERIENCE LEVEL</label>
            <input
              value={level}
              onChange={(e) => setLevel(e.target.value)}
              placeholder="e.g. Mid-level, 3 years"
              className="mt-2 w-full rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-1 focus:ring-accent"
            />
          </div>
          <button
            onClick={() => save({ targetRole: role, experienceLevel: level })}
            className="self-start rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong"
          >
            {saved ? "Saved ✓" : "Save"}
          </button>
        </div>

        <p className="mt-4 border-t border-line pt-4 text-xs text-faint">
          Resume upload and a synced profile are planned — for now this is
          stored locally and used to pre-fill Practice.
        </p>
      </div>

      <div className="mt-6">
        <DossierPreview />
      </div>
    </div>
  );
}
