"use client";

import Link from "next/link";
import { chapters } from "@/lib/content";
import { useAuth } from "@/lib/auth-context";

export default function Nav() {
  const { isAuthenticated, isLoading } = useAuth();

  return (
    <header className="fixed top-0 left-0 right-0 z-50 border-b border-line bg-canvas/70 backdrop-blur-md">
      <div className="mx-auto flex max-w-[1400px] items-center justify-between px-6 py-4 lg:px-10">
        <Link href="/" className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-accent" />
          <span className="font-mono text-sm tracking-wide text-ink">PrepPanel</span>
        </Link>

        <nav className="hidden items-center gap-6 font-mono text-[11px] uppercase tracking-wider text-muted lg:flex">
          {chapters.map((c) => (
            <a
              key={c.num}
              href={`#chapter-${c.num.replace(".", "-")}`}
              className="transition hover:text-ink"
            >
              {c.num} {c.eyebrow}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-4">
          {!isLoading && isAuthenticated ? (
            <Link
              href="/home"
              className="rounded-md border border-lineStrong bg-ink px-4 py-2 font-mono text-xs font-medium text-canvas transition hover:bg-white"
            >
              Dashboard
            </Link>
          ) : (
            <>
              <Link
                href="/login"
                className="hidden font-mono text-xs text-muted transition hover:text-ink sm:block"
              >
                Sign in
              </Link>
              <Link
                href="/register"
                className="rounded-md border border-lineStrong bg-ink px-4 py-2 font-mono text-xs font-medium text-canvas transition hover:bg-white"
              >
                Start a session
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
