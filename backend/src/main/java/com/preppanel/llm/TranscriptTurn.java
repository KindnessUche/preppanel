package com.preppanel.llm;

public record TranscriptTurn(
        String panelistName,
        String questionText,
        String answerText
) {
}
