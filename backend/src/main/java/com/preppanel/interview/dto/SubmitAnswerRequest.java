package com.preppanel.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
        @NotBlank
        @Size(max = 4000, message = "answerText must be 4000 characters or fewer")
        String answerText
) {
}
