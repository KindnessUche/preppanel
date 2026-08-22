package com.preppanel.auth.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
