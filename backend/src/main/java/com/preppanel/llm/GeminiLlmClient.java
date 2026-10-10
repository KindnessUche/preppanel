package com.preppanel.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Gemini via generateContent. The key goes in a header, not the URL, so it can't leak into URL logs. */
@Component
@Slf4j
public class GeminiLlmClient implements LlmClientService {

    private final WebClient webClient = WebClient.create();

    @Override
    public Set<String> providers() {
        return Set.of("gemini");
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, LlmCredentials credentials) {
        String baseUrl = LlmProviders.find("gemini").orElseThrow().baseUrl();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));

        JsonNode response;
        try {
            response = webClient.post()
                    .uri(baseUrl + "/models/{model}:generateContent", credentials.model())
                    .header("x-goog-api-key", credentials.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(LlmHttp.TIMEOUT);
        } catch (RuntimeException e) {
            throw LlmHttp.translate("gemini", e);
        }

        StringBuilder text = new StringBuilder();
        if (response != null) {
            for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
        }
        if (text.toString().isBlank()) {
            throw new LlmProviderException("gemini", "Empty response from Gemini");
        }
        return text.toString().trim();
    }
}
