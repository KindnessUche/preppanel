import type { AuthFetch } from "@/lib/interviews";
import { readJsonOrThrow, type ProfileResponse } from "@/lib/api";

export type { ProfileResponse };

export async function getProfile(authFetch: AuthFetch): Promise<ProfileResponse> {
  const res = await authFetch("/api/profile", { method: "GET" });
  return readJsonOrThrow<ProfileResponse>(res);
}

/** file must be a PDF - the backend rejects anything else. */
export async function uploadResume(authFetch: AuthFetch, file: File): Promise<ProfileResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const res = await authFetch("/api/profile/resume", {
    method: "POST",
    body: formData, // no Content-Type header - the browser sets the multipart boundary itself
  });
  return readJsonOrThrow<ProfileResponse>(res);
}

export async function updatePortfolio(
  authFetch: AuthFetch,
  params: { githubUsername?: string | null; portfolioUrls?: string[] }
): Promise<ProfileResponse> {
  const res = await authFetch("/api/profile/portfolio", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      githubUsername: params.githubUsername ?? null,
      portfolioUrls: params.portfolioUrls ?? [],
    }),
  });
  return readJsonOrThrow<ProfileResponse>(res);
}
