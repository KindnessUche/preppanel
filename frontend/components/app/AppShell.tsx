"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

const TABS = [
  { href: "/home", label: "Home", key: "home" },
  { href: "/prep", label: "Prep", key: "prep" },
  { href: "/practice", label: "Practice", key: "practice" },
  { href: "/growth", label: "Growth", key: "growth" },
  { href: "/account", label: "Account", key: "account" },
];

function TabIcon({ tab, active }: { tab: string; active: boolean }) {
  const stroke = active ? "#5b8def" : "rgba(255,255,255,0.35)";
  const common = { fill: "none", stroke, strokeWidth: 1.6, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

  switch (tab) {
    case "home":
      return <svg viewBox="0 0 24 24" width="20" height="20" {...common}><path d="M3 11l9-7 9 7M5 10v9h14v-9" /></svg>;
    case "prep":
      return <svg viewBox="0 0 24 24" width="20" height="20" {...common}><path d="M6 4h9l3 3v13H6z" /><path d="M9 9h6M9 13h6M9 17h3" /></svg>;
    case "practice":
      return <svg viewBox="0 0 24 24" width="22" height="22" {...common}><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="2.4" /></svg>;
    case "growth":
      return <svg viewBox="0 0 24 24" width="20" height="20" {...common}><path d="M4 19V9M11 19V5M18 19v-7" /></svg>;
    case "account":
      return <svg viewBox="0 0 24 24" width="20" height="20" {...common}><circle cx="12" cy="8" r="3.4" /><path d="M5 20c1.2-3.6 4-5.5 7-5.5s5.8 1.9 7 5.5" /></svg>;
    default:
      return null;
  }
}

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <div className="relative min-h-screen">
      <div className="absolute inset-0 bg-blueprint opacity-[0.07]" />

      {/* Desktop left rail */}
      <aside className="fixed left-0 top-0 z-40 hidden h-screen w-60 flex-col border-r border-line bg-canvas/80 backdrop-blur-md lg:flex">
        <Link href="/home" className="flex items-center gap-2 px-6 py-6">
          <span className="h-2 w-2 rounded-full bg-accent" />
          <span className="font-mono text-sm tracking-wide text-ink">PrepPanel</span>
        </Link>

        <nav className="flex flex-1 flex-col gap-1 px-3">
          {TABS.map((tab) => {
            const active = pathname?.startsWith(tab.href);
            return (
              <Link
                key={tab.key}
                href={tab.href}
                className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition ${
                  active ? "bg-panel text-ink" : "text-muted hover:bg-panel/60 hover:text-ink"
                }`}
              >
                <TabIcon tab={tab.key} active={!!active} />
                {tab.label}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-line px-6 py-5">
          <p className="truncate text-xs text-faint">{user?.email}</p>
          <button
            onClick={handleLogout}
            className="mt-2 font-mono text-[11px] text-muted transition hover:text-ink"
          >
            Sign out
          </button>
        </div>
      </aside>

      {/* Mobile bottom tab bar - Practice visually elevated, per the design doc */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 flex items-end justify-around border-t border-line bg-canvas/90 px-2 pb-2 pt-2 backdrop-blur-md lg:hidden">
        {TABS.map((tab) => {
          const active = pathname?.startsWith(tab.href);
          const isPractice = tab.key === "practice";
          return (
            <Link
              key={tab.key}
              href={tab.href}
              className={`flex flex-col items-center gap-1 px-3 py-1.5 ${
                isPractice ? "-translate-y-2" : ""
              }`}
            >
              <span
                className={`flex items-center justify-center rounded-full transition ${
                  isPractice
                    ? `h-14 w-14 border-2 ${active ? "border-accent bg-panel" : "border-line bg-panel/60"}`
                    : "h-9 w-9"
                }`}
              >
                <TabIcon tab={tab.key} active={!!active} />
              </span>
              <span className={`font-mono text-[10px] ${active ? "text-ink" : "text-faint"}`}>
                {tab.label}
              </span>
            </Link>
          );
        })}
      </nav>

      <main className="relative lg:pl-60">{children}</main>
    </div>
  );
}
