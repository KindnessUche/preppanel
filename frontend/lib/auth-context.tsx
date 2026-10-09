"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
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

interface AuthUser {
  id: string;
  email: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean; // true only during the initial session-restore attempt on mount
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

/**
 * The access token lives only in React state (JS memory) - never in
 * localStorage or a JS-readable cookie - so an XSS payload has nothing to
 * steal. The refresh token lives in an httpOnly cookie the browser manages;
 * this component never sees its value, only whether refresh succeeded.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  // Avoids a duplicate concurrent refresh call if multiple requests 401 at once.
  const refreshInFlight = useRef<Promise<string | null> | null>(null);

  const attemptRefresh = useCallback(async (): Promise<string | null> => {
    if (refreshInFlight.current) return refreshInFlight.current;

    const promise = (async () => {
      try {
        const res = await refreshTokens();
        setAccessToken(res.accessToken);
        return res.accessToken;
      } catch {
        setAccessToken(null);
        return null;
      } finally {
        refreshInFlight.current = null;
      }
    })();

    refreshInFlight.current = promise;
    return promise;
  }, []);

  // On first load, there's no access token in memory yet (a page refresh
  // wipes JS state by design) - try the refresh cookie to silently restore
  // the session, same as any httpOnly-cookie auth flow.
  useEffect(() => {
    attemptRefresh().finally(() => setIsLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const applyAuthResponse = useCallback((res: AuthResponse) => {
    setAccessToken(res.accessToken);
  }, []);

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
    try {
      // Best-effort - the user is logged out client-side regardless of
      // whether this call succeeds (e.g. refresh cookie already expired).
      await logoutUser();
    } catch {
      /* ignore */
    }
    setAccessToken(null);
  }, []);

  const authFetch = useCallback(
    async (path: string, init: RequestInit = {}): Promise<Response> => {
      let current = accessToken;

      // Proactively refresh if the access token is already expired, rather
      // than waiting for a 401 round-trip.
      if (current && isTokenExpired(current)) {
        current = await attemptRefresh();
        if (!current) {
          throw new ApiError(401, "Session expired - please sign in again.");
        }
      }

      const doFetch = (token: string | null) =>
        fetch(`${API_BASE_URL}${path}`, {
          ...init,
          credentials: "include",
          headers: {
            ...(init.headers ?? {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        });

      let res = await doFetch(current);

      // Reactive fallback: if the server rejects the token for any other
      // reason, try exactly one refresh-and-retry before giving up.
      if (res.status === 401) {
        const refreshed = await attemptRefresh();
        if (refreshed) {
          res = await doFetch(refreshed);
        }
      }

      return res;
    },
    [accessToken, attemptRefresh]
  );

  const user = accessToken ? userFromToken(accessToken) : null;

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: !!accessToken,
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
