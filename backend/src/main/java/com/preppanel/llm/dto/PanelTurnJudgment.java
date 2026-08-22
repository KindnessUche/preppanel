package com.preppanel.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PanelTurnJudgment(
        String reaction,        // brief in-character reaction to the previous answer, "" if first question
        String moodShift,       // "positive" | "neutral" | "negative"
        String nextQuestion,    // "" if terminate is true
        Boolean terminate,
        String terminateReason  // null unless terminate is true
) {
}
