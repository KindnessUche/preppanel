"use client";

import { useInView } from "@/lib/useInView";

const logLines = [
  { t: "$", c: "fetch careers.acme.com --respect-robots-txt" },
  { t: "→", c: "Found 4 open Backend Engineer postings" },
  { t: "$", c: "fetch acme.com/engineering-blog --recent 90d" },
  { t: "→", c: "3 posts found: \"Migrating to event sourcing\", \"On-call rewrite\", \"Scaling checkout\"" },
  { t: "✓", c: "Grounding question generation in: event sourcing, on-call practices" },
];

export default function PanelResearch() {
  const { ref, inView } = useInView<HTMLDivElement>(0.3);

  return (
    <div
      ref={ref}
      className="w-full overflow-hidden rounded-2xl border border-line bg-[#050607]"
    >
      <div className="flex items-center justify-between border-b border-line px-5 py-3">
        <div className="flex items-center gap-2">
          <span className="h-2.5 w-2.5 rounded-full bg-red-400/70" />
          <span className="h-2.5 w-2.5 rounded-full bg-amber-400/70" />
          <span className="h-2.5 w-2.5 rounded-full bg-green-400/70" />
        </div>
        <span className="fig-label text-faint">research-agent — planned</span>
      </div>

      <div className="px-5 py-6 font-mono text-[13px] leading-relaxed sm:px-8">
        {logLines.map((line, i) => (
          <div
            key={i}
            className="flex gap-3 opacity-0"
            style={
              inView
                ? { animation: "typeIn 0.4s ease forwards", animationDelay: `${i * 0.55}s` }
                : undefined
            }
          >
            <span className={line.t === "✓" ? "text-accent" : "text-faint"}>{line.t}</span>
            <span className={line.t === "$" ? "text-ink" : "text-muted"}>{line.c}</span>
          </div>
        ))}
      </div>

      <style jsx>{`
        @keyframes typeIn {
          from {
            opacity: 0;
            transform: translateX(-6px);
          }
          to {
            opacity: 1;
            transform: translateX(0);
          }
        }
      `}</style>
    </div>
  );
}
