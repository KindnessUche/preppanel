"use client";

import { useEffect, useRef, useState } from "react";
import { ensureGsapRegistered, ScrollTrigger } from "@/lib/gsap";
import { chapters } from "@/lib/content";

export default function ChapterIndex() {
  const [active, setActive] = useState(chapters[0].num);
  const triggersRef = useRef<ScrollTrigger[]>([]);

  useEffect(() => {
    ensureGsapRegistered();

    const created: ScrollTrigger[] = chapters.map((c) =>
      ScrollTrigger.create({
        trigger: `#chapter-${c.num.replace(".", "-")}`,
        start: "top 50%",
        end: "bottom 50%",
        onToggle: (self) => {
          if (self.isActive) setActive(c.num);
        },
      })
    );
    triggersRef.current = created;

    return () => created.forEach((t) => t.kill());
  }, []);

  return (
    <div className="pointer-events-none fixed left-8 top-1/2 z-40 hidden -translate-y-1/2 xl:block">
      <div className="pointer-events-auto flex flex-col gap-4">
        {chapters.map((c) => {
          const isActive = c.num === active;
          return (
            <a
              key={c.num}
              href={`#chapter-${c.num.replace(".", "-")}`}
              className="group flex items-center gap-3"
            >
              <span
                className={`h-px transition-all duration-300 ${
                  isActive ? "w-8 bg-accent" : "w-4 bg-line group-hover:bg-lineStrong"
                }`}
              />
              <span
                className={`font-mono text-[11px] transition-colors duration-300 ${
                  isActive ? "text-ink" : "text-faint group-hover:text-muted"
                }`}
              >
                {c.num}
              </span>
            </a>
          );
        })}
      </div>
    </div>
  );
}
