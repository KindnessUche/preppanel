"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { getReduceMotion, setReduceMotion } from "@/lib/motion-pref";
import { getProfile, uploadResume, updatePortfolio, type ProfileResponse } from "@/lib/profile";
import { ApiError } from "@/lib/api";
import ApiKeysPanel from "@/components/app/ApiKeysPanel";

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 rounded-full transition ${checked ? "bg-accent" : "bg-line"}`}
    >
      <span
        className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
          checked ? "translate-x-5" : "translate-x-0.5"
        }`}
      />
    </button>
  );
}

function ResumePortfolioPanel() {
  const { authFetch } = useAuth();
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [githubInput, setGithubInput] = useState("");

  useEffect(() => {
    let cancelled = false;
    getProfile(authFetch)
      .then((p) => {
        if (cancelled) return;
        setProfile(p);
        setGithubInput(p.githubUsername ?? "");
      })
      .catch(() => {
        if (!cancelled) setError("Couldn't load your profile.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-selecting the same file later
    if (!file) return;

    setUploading(true);
    setError(null);
    try {
      const updated = await uploadResume(authFetch, file);
      setProfile(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Upload failed - please try again.");
    } finally {
      setUploading(false);
    }
  }

  async function handleSaveGithub() {
    setError(null);
    try {
      const updated = await updatePortfolio(authFetch, {
        githubUsername: githubInput.trim() || null,
        portfolioUrls: profile?.portfolioUrls ?? [],
      });
      setProfile(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save that - please try again.");
    }
  }

  return (
    <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
      <span className="fig-label">RESUME & PORTFOLIO</span>
      <p className="mt-2 text-sm text-muted">
        Panelists tailor questions to your actual background when this is on file.
      </p>

      {loading ? (
        <p className="mt-4 text-xs text-faint">Loading…</p>
      ) : (
        <>
          <div className="mt-4 flex items-center justify-between gap-3">
            <div className="min-w-0">
              {profile?.hasResume ? (
                <>
                  <p className="truncate text-sm text-ink">{profile.resumeFilename}</p>
                  {profile.parsedSkills.length > 0 && (
                    <p className="mt-1 text-xs text-muted">
                      Detected: {profile.parsedSkills.join(", ")}
                    </p>
                  )}
                </>
              ) : (
                <p className="text-sm text-muted">No resume uploaded yet.</p>
              )}
            </div>
            <label className="shrink-0 cursor-pointer rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong">
              {uploading ? "Uploading…" : profile?.hasResume ? "Replace PDF" : "Upload PDF"}
              <input
                type="file"
                accept="application/pdf"
                className="hidden"
                disabled={uploading}
                onChange={handleFileChange}
              />
            </label>
          </div>

          <div className="mt-5 border-t border-line pt-4">
            <p className="text-xs text-muted">GitHub username</p>
            <div className="mt-2 flex gap-2">
              <input
                type="text"
                value={githubInput}
                onChange={(e) => setGithubInput(e.target.value)}
                placeholder="octocat"
                className="min-w-0 flex-1 rounded-md border border-line bg-transparent px-3 py-2 text-sm text-ink outline-none focus:border-lineStrong"
              />
              <button
                onClick={handleSaveGithub}
                className="shrink-0 rounded-md border border-line px-4 py-2 font-mono text-xs text-ink transition hover:border-lineStrong"
              >
                Save
              </button>
            </div>
          </div>

          {error && <p className="mt-3 text-xs text-red-400">{error}</p>}
        </>
      )}
    </div>
  );
}

export default function AccountPage() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const [reduceMotion, setReduceMotionState] = useState(false);

  useEffect(() => {
    setReduceMotionState(getReduceMotion());
  }, []);

  function handleToggleMotion(value: boolean) {
    setReduceMotionState(value);
    setReduceMotion(value);
  }

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <div className="mx-auto max-w-2xl px-6 pb-28 pt-16 lg:pb-16 lg:pt-20">
      <span className="fig-label">ACCOUNT</span>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">
        {user?.email}
      </h1>

      <div className="panel-texture mt-8 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">ACCESSIBILITY</span>
        <div className="mt-4 flex items-center justify-between">
          <div>
            <p className="text-sm text-ink">Reduce motion</p>
            <p className="mt-1 text-xs text-muted">
              Disables the breathing orb and spring animations, falling back to simple fades.
            </p>
          </div>
          <Toggle checked={reduceMotion} onChange={handleToggleMotion} />
        </div>
      </div>

      <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">MIC & SPEAKER TEST</span>
        <p className="mt-2 text-sm text-muted">
          Planned for voice mode — pre-flight your mic before a session instead
          of discovering a problem mid-interview.
        </p>
      </div>

      <ApiKeysPanel />

      <ResumePortfolioPanel />

      <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">PLAN</span>
        <p className="mt-2 text-sm text-ink">Free</p>
        <ul className="mt-3 space-y-1.5 text-xs text-muted">
          <li>1 generic interviewer, 3 tone presets</li>
          <li>Last 3 sessions in Growth, no trend lines</li>
          <li>1 saved company dossier, refreshed monthly</li>
        </ul>
        <p className="mt-4 font-mono text-[11px] text-faint">
          Paid plans and billing aren't live yet.
        </p>
      </div>

      <div className="panel-texture mt-6 rounded-2xl border border-line bg-panel/40 p-6">
        <span className="fig-label">DATA CONTROLS</span>
        <p className="mt-2 text-sm text-muted">
          This product stores your interview transcripts and, if uploaded, your
          resume text. A control to delete your resume or full session history
          is planned — not buried, just not built yet.
        </p>
      </div>

      <button
        onClick={handleLogout}
        className="mt-8 rounded-md border border-line px-5 py-2.5 font-mono text-sm text-ink transition hover:border-lineStrong"
      >
        Sign out
      </button>
    </div>
  );
}
