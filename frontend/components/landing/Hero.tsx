"use client";

import { useEffect, useRef } from "react";
import { ensureGsapRegistered, gsap } from "@/lib/gsap";
import MarqueeRow from "@/components/MarqueeRow";
import MagneticButton from "@/components/MagneticButton";
import { roles } from "@/lib/content";
import { useAuth } from "@/lib/auth-context";

export default function Hero() {
  const containerRef = useRef<HTMLDivElement>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const { isAuthenticated } = useAuth();
  const primaryHref = isAuthenticated ? "/home" : "/register";

  useEffect(() => {
    ensureGsapRegistered();
    const ctx = gsap.context(() => {
      const tl = gsap.timeline({ defaults: { ease: "power4.out" } });

      tl.fromTo(
        ".hero-mask",
        { yPercent: 100 },
        { yPercent: 0, duration: 1.1, stagger: 0.07 }
      )
        .fromTo(
          ".hero-sub",
          { opacity: 0, y: 16 },
          { opacity: 1, y: 0, duration: 0.7 },
          "-=0.6"
        )
        .fromTo(
          ".hero-cta",
          { opacity: 0, y: 12 },
          { opacity: 1, y: 0, duration: 0.6, stagger: 0.08 },
          "-=0.45"
        )
        .fromTo(
          ".hero-card",
          { opacity: 0, y: 40, rotateX: -8 },
          { opacity: 1, y: 0, rotateX: 0, duration: 1.1 },
          "-=0.7"
        )
        .fromTo(
          ".hero-marquee",
          { opacity: 0 },
          { opacity: 1, duration: 0.8 },
          "-=0.4"
        );

      const card = cardRef.current;
      if (card) {
        const onMove = (e: MouseEvent) => {
          const rect = card.getBoundingClientRect();
          const px = (e.clientX - rect.left) / rect.width - 0.5;
          const py = (e.clientY - rect.top) / rect.height - 0.5;
          gsap.to(card, {
            rotateY: px * 6,
            rotateX: -py * 6,
            duration: 0.6,
            ease: "power2.out",
          });
        };
        const onLeave = () => {
          gsap.to(card, { rotateX: 0, rotateY: 0, duration: 0.8, ease: "elastic.out(1,0.5)" });
        };
        window.addEventListener("mousemove", onMove);
        card.addEventListener("mouseleave", onLeave);
        return () => {
          window.removeEventListener("mousemove", onMove);
          card.removeEventListener("mouseleave", onLeave);
        };
      }
    }, containerRef);

    return () => ctx.revert();
  }, []);

  return (
    <section ref={containerRef} className="relative overflow-hidden pt-40 pb-20">
      <div className="absolute inset-0 bg-blueprint opacity-30 [mask-image:radial-gradient(ellipse_60%_50%_at_50%_20%,black,transparent)]" />
      <div className="glow-accent absolute inset-0" />

      <div className="relative mx-auto max-w-[1400px] px-6 lg:px-10">
        <div className="grid grid-cols-1 gap-16 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
          <div>
            <div className="hero-sub mb-6 inline-flex items-center gap-2 rounded-full border border-line px-3 py-1 font-mono text-[11px] uppercase tracking-wider text-muted">
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent" />
              A panel, not a script
            </div>

            <h1 className="text-[13vw] font-medium leading-[0.95] tracking-tight text-ink sm:text-6xl lg:text-7xl">
              <span className="block overflow-hidden">
                <span className="hero-mask block">The panel</span>
              </span>
              <span className="block overflow-hidden">
                <span className="hero-mask block">doesn&rsquo;t go</span>
              </span>
              <span className="block overflow-hidden">
                <span className="hero-mask block text-muted">easy on you.</span>
              </span>
            </h1>

            <p className="hero-sub mt-8 max-w-md text-lg leading-relaxed text-muted">
              PrepPanel runs realistic mock interviews, scores every answer
              against a real rubric, and is building toward a panel of four
              interviewers — each with their own mood, pace, and way of
              catching a vague answer.
            </p>

            <div className="mt-10 flex flex-wrap items-center gap-4">
              <MagneticButton
                href={primaryHref}
                className="hero-cta inline-block rounded-md bg-ink px-6 py-3 font-mono text-sm font-medium text-canvas transition hover:bg-white"
              >
                Start a mock interview
              </MagneticButton>
              <a
                href="#chapter-1-0"
                className="hero-cta font-mono text-sm text-muted transition hover:text-ink"
              >
                Meet the panel →
              </a>
            </div>
          </div>

          <div className="hero-card [perspective:1000px]">
            <div
              ref={cardRef}
              className="panel-texture relative rounded-2xl border border-line bg-panel/60 p-5 shadow-[0_40px_80px_-20px_rgba(0,0,0,0.6)] [transform-style:preserve-3d]"
            >
              <div className="flex items-center justify-between border-b border-line pb-4">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-red-400/70" />
                  <span className="h-2 w-2 rounded-full bg-amber-400/70" />
                  <span className="h-2 w-2 rounded-full bg-green-400/70" />
                </div>
                <span className="fig-label">SESSION · LIVE</span>
              </div>

              <div className="mt-5 flex items-center gap-3">
                <div className="flex h-9 w-9 flex-none items-center justify-center rounded-full border border-line bg-canvas font-mono text-xs text-accent">
                  SK
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-ink">The Skeptic</span>
                    <span className="rounded-full bg-red-400/10 px-2 py-0.5 font-mono text-[10px] text-red-300">
                      pressing
                    </span>
                  </div>
                  <p className="mt-1 text-sm leading-relaxed text-muted">
                    "But what if that dependency wasn't available — what then?"
                  </p>
                </div>
              </div>

              <div className="mt-5 rounded-lg border border-line bg-canvas/60 p-4">
                <span className="fig-label">YOUR ANSWER</span>
                <p className="mt-2 text-sm leading-relaxed text-muted">
                  I'd fall back to a cached snapshot and flag it as stale in
                  the UI rather than block the request entirely...
                </p>
              </div>

              <div className="mt-4 flex items-center justify-between">
                <span className="fig-label">CONTENT 82 · STRUCTURE 76</span>
                <span className="flex items-center gap-1 font-mono text-[11px] text-accent">
                  <span className="h-1.5 w-1.5 rounded-full bg-accent" /> scoring
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="hero-marquee relative mt-24 border-y border-line py-5">
        <MarqueeRow items={roles} speed={38} />
      </div>
    </section>
  );
}
