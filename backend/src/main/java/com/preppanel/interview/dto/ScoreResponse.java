package com.preppanel.interview.dto;

public record ScoreResponse(
        int contentScore,
        int structureScore,
        Integer technicalScore,
        Integer deliveryScore,
        String feedbackText,
        java.util.Map<String, Object> detail
) {
    public ScoreResponse(int contentScore, int structureScore, Integer technicalScore, Integer deliveryScore, String feedbackText) {
        this(contentScore, structureScore, technicalScore, deliveryScore, feedbackText, null);
    }
}
