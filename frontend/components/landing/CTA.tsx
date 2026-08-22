"use client";

import ScrollReveal from "@/components/ScrollReveal";
import MagneticButton from "@/components/MagneticButton";
import { useAuth } from "@/lib/auth-context";

export default function CTA() {
  const { isAuthenticated } = useAuth();
  const primaryHref = isAuthenticated ? "/home" : "/register";

  return (
    <section id="cta" className="relative overflow-hidden py-32">
      <div className="glow-accent absolute inset-0" />
      <div className="absolute inset-0 bg-blueprint opacity-20 [mask-image:radial-gradient(ellipse_50%_60%_at_50%_50%,black,transparent)]" />

      <ScrollReveal className="relative mx-auto max-w-2xl px-6 text-center">
        <span className="fig-label">READY WHEN YOU ARE</span>
        <h2 className="mt-4 text-4xl font-medium tracking-tight text-ink sm:text-5xl">
          Your next interview
          <br />
          <span className="text-muted">deserves a real one first.</span>
        </h2>
        <p className="mx-auto mt-6 max-w-md text-lg leading-relaxed text-muted">
          Pick a role, answer five questions, get a scored report with
          specific feedback — not a participation trophy.
        </p>
        <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
          <MagneticButton
            href={primaryHref}
            className="inline-block rounded-md bg-ink px-7 py-3.5 font-mono text-sm font-medium text-canvas transition hover:bg-white"
          >
            Start a mock interview
          </MagneticButton>
        </div>
      </ScrollReveal>
    </section>
  );
}
