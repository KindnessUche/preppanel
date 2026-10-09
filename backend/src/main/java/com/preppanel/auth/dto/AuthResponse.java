package com.preppanel.auth.dto;

import java.util.UUID;

/**
 * The refresh token is intentionally NOT included here. It's set as an
 * httpOnly, SameSite=Strict cookie by AuthController so client-side JS
 * (and therefore XSS) can never read it; only the short-lived access
 * token is handed back in the body, for the frontend to hold in memory.
 */
public record AuthResponse(
        UUID userId,
        String email,
        String accessToken,
        long expiresInSeconds
) {
}
