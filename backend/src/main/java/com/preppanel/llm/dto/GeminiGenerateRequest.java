package com.preppanel.llm.dto;

import java.util.List;

public record GeminiGenerateRequest(
        List<Content> contents
) {
    public record Content(List<Part> parts) {
    }

    public record Part(String text) {
    }

    public static GeminiGenerateRequest of(String systemPrompt, String userPrompt) {
        // Gemini has no separate system role in this basic endpoint shape - prefix it
        // into the single user turn instead.
        String combined = systemPrompt + "\n\n" + userPrompt;
        return new GeminiGenerateRequest(List.of(new Content(List.of(new Part(combined)))));
    }
}
