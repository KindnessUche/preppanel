"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { getAllSessions, type StoredSessionRef } from "@/lib/session-store";

const FREE_TIER_LIMIT = 3;

export default function GrowthPage() {
  const [sessions, setSessions] = useState<StoredSessionRef[]>([]);

  useEffect(() => {
    setSessions(getAllSessions());
  }, []);

  const visible = sessions.slice(0, FREE_TIER_LIMIT);
  const hiddenCount = Math.max(0, sessions.length - FREE_TIER_LIMIT);

  return (
    <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
      <span className="fig-label">GROWTH</span>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">
        Your track record.
      </h1>
      <p className="mt-2 text-sm text-muted">
        History is tracked on this device for now — account-wide sync is planned.
      </p>

      {visible.length === 0 ? (
        <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-8 text-center">
          <p className="text-muted">No sessions yet — practice one when you're ready.</p>
          <Link
            href="/practice"
            className="mt-5 inline-block rounded-md bg-ink px-5 py-2.5 font-mono text-sm font-medium text-canvas transition hover:bg-white"
          >
            Start a session
          </Link>
        </div>
      ) : (
        <div className="mt-8 divide-y divide-line border-t border-b border-line">
          {visible.map((s) => (
            <Link
              key={s.sessionId}
              href={`/growth/${s.sessionId}`}
              className="flex items-center justify-between px-1 py-4 transition hover:bg-panel/30"
            >
              <div>
                <p className="text-sm text-ink">{s.role}</p>
                <p className="mt-0.5 font-mono text-[11px] text-faint">
                  {new Date(s.startedAt).toLocaleDateString()}
                  {s.panelistNames.length > 0 && ` · ${s.panelistNames.join(", ")}`}
                </p>
              </div>
              <span
                className={`rounded-full px-2.5 py-1 font-mono text-[10px] ${
                  s.completed ? "bg-accent/10 text-accent" : "bg-line text-muted"
                }`}
              >
                {s.completed ? "completed" : "in progress"}
              </span>
            </Link>
          ))}
        </div>
      )}

      {hiddenCount > 0 && (
        <p className="mt-4 font-mono text-[11px] text-faint">
          +{hiddenCount} more session{hiddenCount === 1 ? "" : "s"} — full history & trend lines are a planned paid feature.
        </p>
      )}
    </div>
  );
}
