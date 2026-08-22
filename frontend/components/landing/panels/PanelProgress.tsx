"use client";

import { useEffect, useRef, useState } from "react";
import { ensureGsapRegistered, gsap } from "@/lib/gsap";

const points = [40, 52, 48, 61, 58, 70, 82];
const categories = [
  { label: "Behavioral", value: 78 },
  { label: "Technical", value: 54 },
  { label: "System design", value: 41 },
];

function buildPath() {
  const w = 100;
  const h = 40;
  const step = w / (points.length - 1);
  return points
    .map((p, i) => `${i === 0 ? "M" : "L"} ${i * step} ${h - (p / 100) * h}`)
    .join(" ");
}

export default function PanelProgress() {
  const pathRef = useRef<SVGPathElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [playing, setPlaying] = useState(false);

  useEffect(() => {
    ensureGsapRegistered();
    const path = pathRef.current;
    const container = containerRef.current;
    if (!path || !container) return;

    const length = path.getTotalLength();
    gsap.set(path, { strokeDasharray: length, strokeDashoffset: length });

    const ctx = gsap.context(() => {
      gsap.to(path, {
        strokeDashoffset: 0,
        duration: 1.6,
        ease: "power2.out",
        scrollTrigger: {
          trigger: container,
          start: "top 75%",
        },
      });

      gsap.fromTo(
        ".bar-fill",
        { scaleX: 0 },
        {
          scaleX: 1,
          duration: 1,
          stagger: 0.12,
          ease: "power3.out",
          transformOrigin: "left center",
          scrollTrigger: { trigger: container, start: "top 75%" },
        }
      );
    });

    return () => ctx.revert();
  }, []);

  return (
    <div ref={containerRef} className="grid grid-cols-1 gap-4 lg:grid-cols-[1.3fr_1fr]">
      <div className="panel-texture rounded-2xl border border-line bg-panel/40 p-5 sm:p-6">
        <div className="flex items-center justify-between">
          <span className="fig-label">SCORE TREND · LAST 7 SESSIONS</span>
          <span className="fig-label text-faint">PLANNED</span>
        </div>
        <svg viewBox="0 0 100 40" className="mt-6 h-40 w-full" preserveAspectRatio="none">
          <line x1="0" y1="40" x2="100" y2="40" stroke="rgba(255,255,255,0.08)" strokeWidth="0.3" />
          <line x1="0" y1="20" x2="100" y2="20" stroke="rgba(255,255,255,0.05)" strokeWidth="0.3" />
          <path ref={pathRef} d={buildPath()} fill="none" stroke="#5b8def" strokeWidth="1.2" />
        </svg>

        <div className="mt-6 space-y-3 border-t border-line pt-5">
          {categories.map((c) => (
            <div key={c.label}>
              <div className="mb-1.5 flex justify-between font-mono text-[11px] text-muted">
                <span>{c.label}</span>
                <span>{c.value}</span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-line">
                <div
                  className="bar-fill h-full rounded-full bg-accent"
                  style={{ width: `${c.value}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="panel-texture flex flex-col justify-between rounded-2xl border border-line bg-panel/40 p-5 sm:p-6">
        <div>
          <div className="flex items-center justify-between">
            <span className="fig-label">WEEKLY RECAP</span>
            <span className="fig-label text-faint">PLANNED</span>
          </div>
          <p className="mt-4 text-sm leading-relaxed text-muted">
            "Structure scores climbed 14 points this week — you're closing
            answers cleanly. Technical depth is still the gap: two sessions
            in a row landed under 60 on system-design questions."
          </p>
        </div>

        <button
          onClick={() => setPlaying((v) => !v)}
          className="mt-6 flex items-center gap-3 self-start rounded-full border border-line px-4 py-2 font-mono text-xs text-muted transition hover:border-lineStrong hover:text-ink"
        >
          <span className="flex h-4 w-4 items-center justify-center">
            {playing ? "❚❚" : "▶"}
          </span>
          {playing ? "Playing recap" : "Play recap"}
          <span className="flex items-end gap-0.5">
            {[3, 6, 4, 8, 5].map((h, i) => (
              <span
                key={i}
                className="w-0.5 rounded-full bg-accent transition-all"
                style={{
                  height: playing ? `${h}px` : "3px",
                  transitionDelay: `${i * 60}ms`,
                }}
              />
            ))}
          </span>
        </button>
      </div>
    </div>
  );
}
