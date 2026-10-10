package com.preppanel.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** OpenAI, xAI (Grok) and Groq all speak the same /chat/completions dialect. */
@Component
@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClientService {

    private final WebClient webClient = WebClient.create();

    @Override
    public Set<String> providers() {
        return Set.of("openai", "xai", "groq");
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, LlmCredentials credentials) {
        String provider = credentials.provider();
        String baseUrl = LlmProviders.find(provider).orElseThrow().baseUrl();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", credentials.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        // Newer OpenAI/xAI reasoning models reject custom temperature, so it's only sent to Groq.
        if ("groq".equals(provider)) {
            body.put("temperature", 0.9);
        }

        JsonNode response;
        try {
            response = webClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(LlmHttp.TIMEOUT);
        } catch (RuntimeException e) {
            throw LlmHttp.translate(provider, e);
        }

        String content = response == null ? null : response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new LlmProviderException(provider, "Empty response from " + provider);
        }
        return content.trim();
    }
}
