package com.preppanel.interview.dto;

import com.preppanel.transcription.AudioMetrics;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
        @NotBlank
        @Size(max = 4000, message = "answerText must be 4000 characters or fewer")
        String answerText,

        /** Present only for voice answers (from POST /api/transcribe); null for typed answers. */
        @Valid
        AudioMetrics audioMetrics
) {
    public SubmitAnswerRequest(String answerText) {
        this(answerText, null);
    }
}
