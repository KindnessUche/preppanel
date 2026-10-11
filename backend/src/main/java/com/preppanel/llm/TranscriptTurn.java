package com.preppanel.llm;

public record TranscriptTurn(
        String panelistName,
        String questionText,
        String answerText,
        String turnType // lower-case TurnType name; null is treated as an ordinary topic question
) {
    public TranscriptTurn(String panelistName, String questionText, String answerText) {
        this(panelistName, questionText, answerText, null);
    }
}
