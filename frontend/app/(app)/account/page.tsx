"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { getReduceMotion, setReduceMotion } from "@/lib/motion-pref";

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 rounded-full transition ${checked ? "bg-accent" : "bg-line"}`}
    >
      <span
        className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
          checked ? "translate-x-5" : "translate-x-0.5"
        }`}
      />
    </button>
  );
}

export default function AccountPage() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const [reduceMotion, setReduceMotionState] = useState(false);

  useEffect(() => {
    setReduceMotionState(getReduceMotion());
  }, []);

  function handleToggleMotion(value: boolean) {
    setReduceMotionState(value);
    setReduceMotion(value);
  }

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
      <span className="fig-label">ACCOUNT</span>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">
        {user?.email}
      </h1>

      <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">ACCESSIBILITY</span>
        <div className="mt-4 flex items-center justify-between">
          <div>
            <p className="text-sm text-ink">Reduce motion</p>
            <p className="mt-1 text-xs text-muted">
              Disables the breathing orb and spring animations, falling back to simple fades.
            </p>
          </div>
          <Toggle checked={reduceMotion} onChange={handleToggleMotion} />
        </div>
      </div>

      <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">MIC & SPEAKER TEST</span>
        <p className="mt-2 text-sm text-muted">
          Planned for voice mode — pre-flight your mic before a session instead
          of discovering a problem mid-interview.
        </p>
      </div>

      <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">PLAN</span>
        <p className="mt-2 text-sm text-ink">Free</p>
        <ul className="mt-3 space-y-1.5 text-xs text-muted">
          <li>1 generic interviewer, 3 tone presets</li>
          <li>Last 3 sessions in Growth, no trend lines</li>
          <li>1 saved company dossier, refreshed monthly</li>
        </ul>
        <p className="mt-4 font-mono text-[11px] text-faint">
          Paid plans and billing aren't live yet.
        </p>
      </div>

      <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">DATA CONTROLS</span>
        <p className="mt-2 text-sm text-muted">
          This product stores your interview transcripts. Controls to delete
          your resume, a company dossier, or your full session history are
          planned — not buried, just not built yet.
        </p>
      </div>

      <button
        onClick={handleLogout}
        className="mt-8 rounded-md border border-line px-5 py-2.5 font-mono text-sm text-ink transition hover:border-lineStrong"
      >
        Sign out
      </button>
    </div>
  );
}
