"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { getMostRecentIncomplete, getCompletedInLastNDays, type StoredSessionRef } from "@/lib/session-store";

function MomentumStrip() {
  const [days, setDays] = useState<{ date: string; active: boolean }[]>([]);

  useEffect(() => {
    const completed = getCompletedInLastNDays(14);
    const activeDates = new Set(completed.map((s) => s.startedAt.slice(0, 10)));

    const strip = Array.from({ length: 14 }, (_, i) => {
      const d = new Date();
      d.setDate(d.getDate() - (13 - i));
      const iso = d.toISOString().slice(0, 10);
      return { date: iso, active: activeDates.has(iso) };
    });
    setDays(strip);
  }, []);

  return (
    <div className="flex gap-1.5">
      {days.map((d) => (
        <span
          key={d.date}
          className={`h-2.5 w-2.5 rounded-sm ${d.active ? "bg-accent" : "bg-line"}`}
          title={d.date}
        />
      ))}
    </div>
  );
}

export default function HomePage() {
  const { user } = useAuth();
  const [nextUp, setNextUp] = useState<StoredSessionRef | null>(null);

  useEffect(() => {
    setNextUp(getMostRecentIncomplete());
  }, []);

  const firstName = user?.email?.split("@")[0] ?? "there";

  return (
    <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
      <span className="fig-label">TODAY</span>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">
        Hey, {firstName}.
      </h1>

      <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6">
        {nextUp ? (
          <>
            <span className="fig-label">CONTINUE WHERE YOU LEFT OFF</span>
            <p className="mt-2 text-lg text-ink">
              {nextUp.role}
              {nextUp.panelistNames.length > 0 && ` · ${nextUp.panelistNames.join(", ")}`}
            </p>
            <Link
              href="/practice"
              className="mt-5 inline-block rounded-md bg-ink px-5 py-2.5 font-mono text-sm font-medium text-canvas transition hover:bg-white"
            >
              Continue
            </Link>
          </>
        ) : (
          <>
            <span className="fig-label">READY WHEN YOU ARE</span>
            <p className="mt-2 text-lg text-ink">No session in progress.</p>
            <Link
              href="/practice"
              className="mt-5 inline-block rounded-md bg-ink px-5 py-2.5 font-mono text-sm font-medium text-canvas transition hover:bg-white"
            >
              Start a session
            </Link>
          </>
        )}
      </div>

      <div className="mt-8 flex items-center justify-between border-t border-line pt-6">
        <div>
          <span className="fig-label">LAST 14 DAYS</span>
          <p className="mt-1 text-xs text-faint">On this device.</p>
        </div>
        <MomentumStrip />
      </div>

      <div className="mt-8 rounded-xl border border-line bg-panel/20 p-5">
        <span className="fig-label text-faint">RECENT INTEL</span>
        <p className="mt-2 text-sm text-muted">
          Company research isn't live yet — once it is, updates on companies
          you're tracking will show up here.
        </p>
      </div>
    </div>
  );
}
