"use client";

import { useEffect, useRef } from "react";
import { gsap } from "@/lib/gsap";

interface ScoreRadarProps {
  content: number | null;
  structure: number | null;
  technical: number | null;
  delivery: number | null;
}

const AXES = [
  { key: "content", label: "Content", angle: -90 },
  { key: "structure", label: "Structure", angle: 0 },
  { key: "delivery", label: "Delivery", angle: 90 },
  { key: "technical", label: "Technical", angle: 180 },
] as const;

function point(angleDeg: number, radius: number, cx: number, cy: number) {
  const rad = (angleDeg * Math.PI) / 180;
  return { x: cx + radius * Math.cos(rad), y: cy + radius * Math.sin(rad) };
}

export default function ScoreRadar({ content, structure, technical, delivery }: ScoreRadarProps) {
  const polygonRef = useRef<SVGPolygonElement>(null);
  const values: Record<string, number | null> = { content, structure, technical, delivery };

  const size = 220;
  const cx = size / 2;
  const cy = size / 2;
  const maxR = 78;

  const polygonPoints = AXES.map((axis) => {
    const v = values[axis.key];
    const r = v == null ? 0 : (v / 100) * maxR;
    const p = point(axis.angle, r, cx, cy);
    return `${p.x},${p.y}`;
  }).join(" ");

  useEffect(() => {
    if (!polygonRef.current) return;
    gsap.fromTo(
      polygonRef.current,
      { opacity: 0, scale: 0.7, transformOrigin: "center center" },
      { opacity: 1, scale: 1, duration: 0.9, ease: "power3.out", delay: 0.1 }
    );
  }, []);

  return (
    <div className="flex flex-col items-center">
      <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size}>
        {[0.33, 0.66, 1].map((ring) => (
          <polygon
            key={ring}
            points={AXES.map((a) => {
              const p = point(a.angle, maxR * ring, cx, cy);
              return `${p.x},${p.y}`;
            }).join(" ")}
            fill="none"
            stroke="rgba(255,255,255,0.08)"
            strokeWidth="1"
          />
        ))}
        {AXES.map((a) => {
          const p = point(a.angle, maxR, cx, cy);
          return (
            <line key={a.key} x1={cx} y1={cy} x2={p.x} y2={p.y} stroke="rgba(255,255,255,0.08)" />
          );
        })}
        <polygon
          ref={polygonRef}
          points={polygonPoints}
          fill="rgba(91,141,239,0.18)"
          stroke="#5b8def"
          strokeWidth="1.4"
        />
        {AXES.map((a) => {
          const v = values[a.key];
          const labelPoint = point(a.angle, maxR + 24, cx, cy);
          return (
            <text
              key={a.key}
              x={labelPoint.x}
              y={labelPoint.y}
              textAnchor="middle"
              dominantBaseline="middle"
              fontFamily="var(--font-mono)"
              fontSize="10"
              fill={v == null ? "#55595e" : "#8b9096"}
            >
              {a.label} {v == null ? "—" : `▲${v}`}
            </text>
          );
        })}
      </svg>
    </div>
  );
}
