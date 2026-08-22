"use client";

import { useEffect, useRef } from "react";
import { ensureGsapRegistered, gsap } from "@/lib/gsap";

interface MarqueeRowProps {
  items: string[];
  direction?: "left" | "right";
  speed?: number; // seconds for one full loop
  variant?: "chip" | "plain";
}

export default function MarqueeRow({
  items,
  direction = "left",
  speed = 32,
  variant = "chip",
}: MarqueeRowProps) {
  const trackRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    ensureGsapRegistered();
    const el = trackRef.current;
    if (!el) return;

    const ctx = gsap.context(() => {
      const distance = el.scrollWidth / 2;
      gsap.fromTo(
        el,
        { x: direction === "left" ? 0 : -distance },
        {
          x: direction === "left" ? -distance : 0,
          duration: speed,
          ease: "none",
          repeat: -1,
        }
      );
    });

    return () => ctx.revert();
  }, [direction, speed]);

  const doubled = [...items, ...items];

  return (
    <div className="overflow-hidden">
      <div ref={trackRef} className="flex w-max items-center gap-4">
        {doubled.map((item, i) =>
          variant === "chip" ? (
            <span
              key={i}
              className="flex-none rounded-full border border-line px-5 py-2 font-mono text-sm text-muted"
            >
              {item}
            </span>
          ) : (
            <span
              key={i}
              className="flex-none font-mono text-sm text-faint after:ml-4 after:content-['/']"
            >
              {item}
            </span>
          )
        )}
      </div>
    </div>
  );
}
