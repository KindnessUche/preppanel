"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { readJsonOrThrow, ApiError } from "@/lib/api";
import { getSessionReport, scoreSession, type SessionReportResponse } from "@/lib/interviews";
import ScoreRadar from "@/components/app/ScoreRadar";

export default function ReportPage() {
  const params = useParams<{ sessionId: string }>();
  const { authFetch } = useAuth();

  const [report, setReport] = useState<SessionReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [scoring, setScoring] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await getSessionReport(authFetch, params.sessionId);
      const data = await readJsonOrThrow<SessionReportResponse>(res);
      setReport(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load this report.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.sessionId]);

  async function handleScoreNow() {
    setScoring(true);
    try {
      await scoreSession(authFetch, params.sessionId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not score this session.");
    } finally {
      setScoring(false);
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <span className="fig-label text-faint">Loading report…</span>
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="mx-auto max-w-2xl px-6 pt-20">
        <div className="rounded-lg border border-red-400/30 bg-red-400/10 px-4 py-3 text-sm text-red-300">
          {error ?? "Report not found."}
        </div>
      </div>
    );
  }

  const hasAnyScore = report.items.some((i) => i.score !== null);
  const lastFeedback = [...report.items].reverse().find((i) => i.score)?.score?.feedbackText;

  return (
    <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
      <span className="fig-label">DEBRIEF</span>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">
        {report.role}
      </h1>
      <p className="mt-1 font-mono text-xs text-faint">{report.tone} tone</p>

      <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6 sm:p-8">
        {hasAnyScore ? (
          <div className="flex justify-center">
            <ScoreRadar
              content={report.averageContentScore ?? null}
              structure={report.averageStructureScore ?? null}
              technical={null}
              delivery={null}
            />
          </div>
        ) : (
          <div className="text-center">
            <p className="text-sm text-muted">Not scored yet.</p>
            <button
              onClick={handleScoreNow}
              disabled={scoring}
              className="mt-4 rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong disabled:opacity-50"
            >
              {scoring ? "Scoring…" : "Score this session"}
            </button>
          </div>
        )}

        <p className="mt-6 border-t border-line pt-5 text-xs text-faint">
          Delivery and technical scores are captured in voice mode and
          technical-question sessions — not used here.
        </p>

        {lastFeedback && (
          <div className="mt-6 border-t border-line pt-5">
            <span className="fig-label">FOCUS NEXT TIME</span>
            <p className="mt-2 text-sm leading-relaxed text-muted">{lastFeedback}</p>
          </div>
        )}
      </div>

      <div className="mt-6 divide-y divide-line border-t border-b border-line">
        {report.items.map((item) => (
          <div key={item.sequenceNum} className="py-4">
            <button
              onClick={() => setExpanded(expanded === item.sequenceNum ? null : item.sequenceNum)}
              className="flex w-full items-center justify-between gap-4 text-left"
            >
              <span className="text-sm text-ink">
                Q{item.sequenceNum}
                {item.panelistName && (
                  <span className="text-muted"> · {item.panelistName}</span>
                )}
                {" — "}{item.questionText}
              </span>
              {item.score && (
                <span className="flex-none rounded-full bg-accent/10 px-2.5 py-1 font-mono text-[10px] text-accent">
                  {item.score.contentScore}
                </span>
              )}
            </button>
            {expanded === item.sequenceNum && (
              <div className="mt-3 space-y-3 rounded-lg border border-line bg-canvas/60 p-4">
                <div>
                  <span className="fig-label">YOUR ANSWER</span>
                  <p className="mt-1 text-sm text-muted">{item.answerText ?? "Not answered."}</p>
                </div>
                {item.score && (
                  <div>
                    <span className="fig-label">FEEDBACK</span>
                    <p className="mt-1 text-sm text-muted">{item.score.feedbackText}</p>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="mt-8 flex flex-wrap gap-4">
        <Link
          href="/practice"
          className="rounded-md bg-ink px-6 py-3 font-mono text-sm font-medium text-canvas transition hover:bg-white"
        >
          Practice again
        </Link>
        <button
          disabled
          title="Coming soon"
          className="rounded-md border border-line px-6 py-3 font-mono text-sm text-faint opacity-50"
        >
          Export PDF
        </button>
      </div>
    </div>
  );
}
