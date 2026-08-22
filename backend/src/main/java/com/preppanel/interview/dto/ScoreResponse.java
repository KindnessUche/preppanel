package com.preppanel.interview.dto;

public record ScoreResponse(
        int contentScore,
        int structureScore,
        Integer technicalScore,
        Integer deliveryScore,
        String feedbackText
) {
}
