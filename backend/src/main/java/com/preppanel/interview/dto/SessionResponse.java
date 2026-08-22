package com.preppanel.interview.dto;

import java.util.List;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        String role,
        String mode,
        String tone,
        List<PanelistSelection> panelists,
        String status,
        QuestionResponse currentQuestion, // null if session just completed
        boolean completed,
        boolean terminatedByPanel,
        String terminationReason
) {
}
