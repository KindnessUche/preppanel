package com.preppanel.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String primaryProvider,
        String fallbackProvider,
        Groq groq,
        Gemini gemini
) {
    public record Groq(String apiKey, String baseUrl, String model) {
    }

    public record Gemini(String apiKey, String baseUrl, String model) {
    }
}
