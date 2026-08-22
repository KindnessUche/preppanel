"use client";

export default function PanelScore() {
  return (
    <div className="panel-texture rounded-2xl border border-line bg-panel/40">
      <div className="flex items-center justify-between border-b border-line px-5 py-3 sm:px-6">
        <span className="fig-label">ANSWER REVIEW · Q3 OF 5</span>
        <span className="fig-label text-faint">LLM JUDGE</span>
      </div>

      <div className="px-5 py-6 sm:px-6">
        <span className="fig-label">YOUR ANSWER</span>
        <p className="mt-3 text-[15px] leading-[1.9] text-ink">
          I{" "}
          <mark className="rounded bg-green-400/15 px-1 text-green-300">
            noticed the connection pool was maxing out under load
          </mark>{" "}
          so I{" "}
          <mark className="rounded bg-green-400/15 px-1 text-green-300">
            added a circuit breaker and increased pool size
          </mark>
          , which{" "}
          <mark className="rounded bg-line px-1 text-muted">
            fixed it
          </mark>
          .
        </p>

        <div className="mt-5 flex flex-wrap gap-4 border-t border-line pt-5">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-green-400/70" />
            <span className="font-mono text-xs text-muted">Specific, technical detail</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-faint" />
            <span className="font-mono text-xs text-muted">Vague outcome — quantify it</span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 divide-x divide-line border-t border-line">
        <div className="px-5 py-5 sm:px-6">
          <span className="fig-label">CONTENT</span>
          <div className="mt-2 flex items-baseline gap-1">
            <span className="text-3xl font-medium text-ink">82</span>
            <span className="font-mono text-xs text-faint">/100</span>
          </div>
        </div>
        <div className="px-5 py-5 sm:px-6">
          <span className="fig-label">STRUCTURE</span>
          <div className="mt-2 flex items-baseline gap-1">
            <span className="text-3xl font-medium text-ink">76</span>
            <span className="font-mono text-xs text-faint">/100</span>
          </div>
        </div>
      </div>

      <div className="border-t border-line px-5 py-5 sm:px-6">
        <span className="fig-label">FEEDBACK</span>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          Strong technical specificity on diagnosis and fix. "Fixed it" is
          doing a lot of work — name the actual before/after metric (error
          rate, latency, pool utilization) to make the impact concrete.
        </p>
      </div>
    </div>
  );
}
