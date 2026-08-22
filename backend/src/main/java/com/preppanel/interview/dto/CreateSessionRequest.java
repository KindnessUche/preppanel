package com.preppanel.interview.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateSessionRequest(
        @NotBlank String role,
        String mode,  // "text" (default) | "voice" - voice not implemented yet, accepted for forward-compat
        String tone,  // "friendly" | "neutral" (default) | "direct" - used only if panelists is empty
        List<PanelistSelection> panelists, // 1-4 panelists; falls back to a single neutral panelist if empty
        String experienceLevel // optional: "junior" | "mid" | "senior" | "staff" - calibrates difficulty
) {
}
