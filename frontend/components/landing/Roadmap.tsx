"use client";

import { useEffect, useRef } from "react";
import { ensureGsapRegistered, gsap } from "@/lib/gsap";
import ScrollReveal from "@/components/ScrollReveal";
import { roadmap, statusMeta } from "@/lib/content";

export default function Roadmap() {
  const trackRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    ensureGsapRegistered();
    const el = trackRef.current;
    if (!el) return;

    const ctx = gsap.context(() => {
      gsap.fromTo(
        el,
        { scaleX: 0 },
        {
          scaleX: 1,
          transformOrigin: "left center",
          ease: "none",
          scrollTrigger: {
            trigger: el,
            start: "top 80%",
            end: "bottom 60%",
            scrub: 0.6,
          },
        }
      );
    });

    return () => ctx.revert();
  }, []);

  return (
    <section id="roadmap" className="mx-auto max-w-[1400px] px-6 py-28 lg:px-10">
      <ScrollReveal className="max-w-2xl">
        <span className="fig-label">BUILD LOG</span>
        <h2 className="mt-4 text-4xl font-medium tracking-tight text-ink sm:text-5xl">
          The plan, shipped
          <br />
          <span className="text-muted">in the open.</span>
        </h2>
      </ScrollReveal>

      <div className="relative mt-20">
        <div className="absolute left-0 right-0 top-[7px] h-px bg-line" />
        <div ref={trackRef} className="absolute left-0 right-0 top-[7px] h-px bg-accent" />

        <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-6">
          {roadmap.map((item, i) => {
            const meta = statusMeta[item.status];
            return (
              <ScrollReveal key={item.title} delay={i * 0.06}>
                <div className={`h-3.5 w-3.5 rounded-full ${meta.dot} ring-4 ring-canvas`} />
                <h4 className="mt-6 text-base font-medium text-ink">{item.title}</h4>
                <p className="mt-2 text-sm leading-relaxed text-muted">{item.body}</p>
                <span className="fig-label mt-4 block">{item.date}</span>
              </ScrollReveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
