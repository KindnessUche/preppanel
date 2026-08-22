"use client";

import { useInView } from "@/lib/useInView";

const lines = [
  { speaker: "panel", who: "The Advocate", tag: "encouraging", text: "Tell me about a time you had to debug something under real time pressure." },
  { speaker: "you", who: "You", text: "Sure — we had a checkout endpoint timing out during a flash sale..." },
  { speaker: "panel", who: "The Advocate", tag: "interjecting", text: "Quick one — was that timeout on the DB call or upstream?" },
  { speaker: "you", who: "You", text: "The DB call. Connection pool was exhausted under load." },
  { speaker: "system", who: "PrepPanel", text: "Generating next question — aware of 2 prior answers in this session." },
];

export default function PanelPractice() {
  const { ref, inView } = useInView<HTMLDivElement>(0.3);

  return (
    <div
      ref={ref}
      className="panel-texture w-full rounded-2xl border border-line bg-panel/40 p-5 sm:p-8"
    >
      <div className="flex items-center justify-between border-b border-line pb-4">
        <span className="fig-label">SESSION TRANSCRIPT · BACKEND ENGINEER</span>
        <span className="flex items-center gap-1.5 font-mono text-[11px] text-accent">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent" />
          live
        </span>
      </div>

      <div className="mt-6 flex flex-col gap-5">
        {lines.map((line, i) => (
          <div
            key={i}
            className="flex items-start gap-3 opacity-0"
            style={
              inView
                ? {
                    animation: `fadeSlideIn 0.6s ease forwards`,
                    animationDelay: `${i * 0.5}s`,
                  }
                : undefined
            }
          >
            <div
              className={`mt-0.5 flex h-7 w-7 flex-none items-center justify-center rounded-full border font-mono text-[10px] ${
                line.speaker === "you"
                  ? "border-line text-muted"
                  : line.speaker === "system"
                  ? "border-accent/40 text-accent"
                  : "border-line text-ink"
              }`}
            >
              {line.who.split(" ").map((w) => w[0]).join("").slice(0, 2)}
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-ink">{line.who}</span>
                {"tag" in line && line.tag && (
                  <span
                    className={`rounded-full px-2 py-0.5 font-mono text-[10px] ${
                      line.tag === "interjecting"
                        ? "bg-amber-400/10 text-amber-300"
                        : "bg-accent/10 text-accent"
                    }`}
                  >
                    {line.tag}
                  </span>
                )}
              </div>
              <p className="mt-1 text-sm leading-relaxed text-muted">{line.text}</p>
            </div>
          </div>
        ))}
      </div>

      <style jsx>{`
        @keyframes fadeSlideIn {
          from {
            opacity: 0;
            transform: translateY(10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
      `}</style>
    </div>
  );
}
