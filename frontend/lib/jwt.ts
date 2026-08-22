interface JwtPayload {
  sub?: string;
  email?: string;
  iat?: number;
  exp?: number;
}

/**
 * Decodes a JWT payload WITHOUT verifying its signature. This is only ever
 * used to read the email/expiry for display purposes in the UI - the actual
 * security boundary is the backend, which verifies every token's signature
 * on every request. Never trust this decode for an access decision.
 */
export function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const [, payloadB64] = token.split(".");
    if (!payloadB64) return null;
    const json = atob(payloadB64.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function isTokenExpired(token: string): boolean {
  const payload = decodeJwtPayload(token);
  if (!payload?.exp) return true;
  return Date.now() >= payload.exp * 1000;
}
