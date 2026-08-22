"use client";

import Link from "next/link";

interface AuthShellProps {
  eyebrow: string;
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
}

export default function AuthShell({ eyebrow, title, subtitle, children, footer }: AuthShellProps) {
  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden px-6 py-24">
      <div className="absolute inset-0 bg-blueprint opacity-30 [mask-image:radial-gradient(ellipse_60%_50%_at_50%_30%,black,transparent)]" />
      <div className="glow-accent absolute inset-0" />

      <div className="relative w-full max-w-md">
        <Link href="/" className="mb-10 flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-accent" />
          <span className="font-mono text-sm tracking-wide text-ink">PrepPanel</span>
        </Link>

        <span className="fig-label">{eyebrow}</span>
        <h1 className="mt-3 text-3xl font-medium tracking-tight text-ink">{title}</h1>
        <p className="mt-2 text-sm leading-relaxed text-muted">{subtitle}</p>

        <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6 sm:p-8">
          {children}
        </div>

        <div className="mt-6 text-center text-sm text-muted">{footer}</div>
      </div>
    </main>
  );
}
