package com.preppanel.interview.dto;

import java.util.List;
import java.util.UUID;

public record SessionReportResponse(
        UUID sessionId,
        String role,
        String tone,
        String status,
        Double averageContentScore,
        Double averageStructureScore,
        List<Item> items
) {
    public record Item(
            int sequenceNum,
            String questionText,
            String panelistName,
            String answerText,
            ScoreResponse score // null if this answer hasn't been scored yet
    ) {
    }
}
