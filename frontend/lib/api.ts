const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export interface AuthResponse {
  userId: string;
  email: string;
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
}

/** Thrown on any non-2xx response, carrying the backend's actual error message when present. */
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

async function parseErrorMessage(res: Response): Promise<string> {
  try {
    const body: ApiErrorBody = await res.json();
    if (body.message) return body.message;
  } catch {
    // Response wasn't JSON - fall through to generic message below.
  }
  if (res.status === 0) return "Could not reach the server. Is the backend running?";
  return `Request failed (${res.status})`;
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    // Network-level failure (backend not running, CORS block, DNS, etc.)
    throw new ApiError(0, "Could not reach the server. Is the backend running on :8080?");
  }

  if (!res.ok) {
    throw new ApiError(res.status, await parseErrorMessage(res));
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export function registerUser(email: string, password: string): Promise<AuthResponse> {
  return postJson<AuthResponse>("/api/auth/register", { email, password });
}

export function loginUser(email: string, password: string): Promise<AuthResponse> {
  return postJson<AuthResponse>("/api/auth/login", { email, password });
}

export function refreshTokens(refreshToken: string): Promise<AuthResponse> {
  return postJson<AuthResponse>("/api/auth/refresh", { refreshToken });
}

export function logoutUser(refreshToken: string): Promise<void> {
  return postJson<void>("/api/auth/logout", { refreshToken });
}

export async function readJsonOrThrow<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new ApiError(res.status, await parseErrorMessage(res));
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export { API_BASE_URL };
