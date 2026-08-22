"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
} from "react";
import {
  registerUser,
  loginUser,
  logoutUser,
  refreshTokens,
  ApiError,
  API_BASE_URL,
  type AuthResponse,
} from "@/lib/api";
import { decodeJwtPayload, isTokenExpired } from "@/lib/jwt";

const STORAGE_KEY = "preppanel_auth";

interface StoredAuth {
  accessToken: string;
  refreshToken: string;
}

interface AuthUser {
  id: string;
  email: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean; // true only during the initial localStorage read on mount
  register: (email: string, password: string) => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Wraps fetch with the current access token, retrying once via refresh on a 401. */
  authFetch: (path: string, init?: RequestInit) => Promise<Response>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function userFromToken(accessToken: string): AuthUser | null {
  const payload = decodeJwtPayload(accessToken);
  if (!payload?.sub || !payload.email) return null;
  return { id: payload.sub, email: payload.email };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [tokens, setTokens] = useState<StoredAuth | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Restore session from localStorage on first load.
  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed: StoredAuth = JSON.parse(raw);
        if (parsed.accessToken && parsed.refreshToken) {
          setTokens(parsed);
        }
      }
    } catch {
      // Corrupt/blocked storage - just start logged out.
    } finally {
      setIsLoading(false);
    }
  }, []);

  const persist = useCallback((next: StoredAuth | null) => {
    setTokens(next);
    if (next) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }, []);

  const applyAuthResponse = useCallback(
    (res: AuthResponse) => {
      persist({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    },
    [persist]
  );

  const register = useCallback(
    async (email: string, password: string) => {
      const res = await registerUser(email, password);
      applyAuthResponse(res);
    },
    [applyAuthResponse]
  );

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await loginUser(email, password);
      applyAuthResponse(res);
    },
    [applyAuthResponse]
  );

  const logout = useCallback(async () => {
    if (tokens?.refreshToken) {
      // Best-effort - the user is logged out client-side regardless of whether
      // this call succeeds (e.g. token already expired server-side).
      try {
        await logoutUser(tokens.refreshToken);
      } catch {
        /* ignore */
      }
    }
    persist(null);
  }, [tokens, persist]);

  const authFetch = useCallback(
    async (path: string, init: RequestInit = {}): Promise<Response> => {
      let current = tokens;

      // Proactively refresh if the access token is already expired, rather
      // than waiting for a 401 round-trip.
      if (current && isTokenExpired(current.accessToken)) {
        try {
          const refreshed = await refreshTokens(current.refreshToken);
          current = { accessToken: refreshed.accessToken, refreshToken: refreshed.refreshToken };
          persist(current);
        } catch {
          persist(null);
          throw new ApiError(401, "Session expired - please sign in again.");
        }
      }

      const doFetch = (accessToken: string | null) =>
        fetch(`${API_BASE_URL}${path}`, {
          ...init,
          headers: {
            ...(init.headers ?? {}),
            ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          },
        });

      let res = await doFetch(current?.accessToken ?? null);

      // Reactive fallback: if the server rejects the token for any other
      // reason, try exactly one refresh-and-retry before giving up.
      if (res.status === 401 && current?.refreshToken) {
        try {
          const refreshed = await refreshTokens(current.refreshToken);
          persist({ accessToken: refreshed.accessToken, refreshToken: refreshed.refreshToken });
          res = await doFetch(refreshed.accessToken);
        } catch {
          persist(null);
        }
      }

      return res;
    },
    [tokens, persist]
  );

  const user = tokens ? userFromToken(tokens.accessToken) : null;

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken: tokens?.accessToken ?? null,
        isAuthenticated: !!tokens,
        isLoading,
        register,
        login,
        logout,
        authFetch,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
