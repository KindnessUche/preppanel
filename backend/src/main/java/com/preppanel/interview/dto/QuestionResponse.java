package com.preppanel.interview.dto;

import java.util.UUID;

public record QuestionResponse(
        UUID questionId,
        int sequenceNum,
        String questionText,
        String questionType,
        String panelistArchetypeId, // null if from the static bank (no panelist personality)
        String panelistName,
        String reactionText, // this panelist's reaction to your PREVIOUS answer; "" for the first question
        String moodShift // "positive" | "neutral" | "negative" | null (bank fallback has no mood signal)
) {
}
