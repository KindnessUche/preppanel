package com.preppanel.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreJudgment(
        Integer contentScore,
        Integer structureScore,
        Integer technicalScore, // nullable - only present for technical questions
        String feedbackText
) {
}
