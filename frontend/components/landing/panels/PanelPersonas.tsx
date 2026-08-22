"use client";

import { useState } from "react";
import { personas } from "@/lib/content";
import Orb from "@/components/app/Orb";

export default function PanelPersonas() {
  const [active, setActive] = useState(personas[0].id);
  const activePersona = personas.find((p) => p.id === active) ?? personas[0];

  return (
    <div className="panel-texture rounded-2xl border border-line bg-panel/40 p-4 sm:p-6">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {personas.map((p) => {
          const isActive = p.id === active;
          return (
            <button
              key={p.id}
              onMouseEnter={() => setActive(p.id)}
              onClick={() => setActive(p.id)}
              className={`group relative overflow-hidden rounded-xl border p-4 text-left transition-all duration-300 ${
                isActive
                  ? "border-lineStrong bg-canvas"
                  : "border-line bg-canvas/40 hover:border-lineStrong"
              }`}
            >
              <span
                className="absolute -right-6 -top-6 h-16 w-16 rounded-full opacity-20 blur-2xl transition-opacity duration-300 group-hover:opacity-40"
                style={{ background: p.moodColor }}
              />
              <Orb state={isActive ? "speaking" : "idle"} size={22} color={p.moodColor} />
              <h4 className="mt-3 text-sm font-medium text-ink">{p.name}</h4>
              <p className="mt-1 font-mono text-[11px] text-faint">{p.archetype}</p>
              <span
                className="mt-2 inline-block font-mono text-[10px]"
                style={{ color: isActive ? p.moodColor : "#55595e" }}
              >
                {p.mood}
              </span>
            </button>
          );
        })}
      </div>

      <div className="mt-4 rounded-xl border border-line bg-canvas/60 p-5">
        <span className="fig-label">SAMPLE QUESTION</span>
        <p className="mt-2 text-base leading-relaxed text-ink">
          "{activePersona.sample}"
        </p>
        <p className="mt-3 text-sm leading-relaxed text-muted">{activePersona.bio}</p>
      </div>
    </div>
  );
}
