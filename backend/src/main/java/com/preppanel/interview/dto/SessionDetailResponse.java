package com.preppanel.interview.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionDetailResponse(
        UUID sessionId,
        String role,
        String mode,
        String tone,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<QuestionAndAnswer> items
) {
    public record QuestionAndAnswer(
            UUID questionId,
            int sequenceNum,
            String questionText,
            String questionType,
            String panelistName,
            String reactionText,
            String answerText // null if not yet answered
    ) {
    }
}
