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

/** Claude via the Anthropic Messages API. */
@Component
@Slf4j
public class AnthropicLlmClient implements LlmClientService {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient = WebClient.create();

    @Override
    public Set<String> providers() {
        return Set.of("anthropic");
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, LlmCredentials credentials) {
        String baseUrl = LlmProviders.find("anthropic").orElseThrow().baseUrl();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", credentials.model());
        body.put("max_tokens", 2048);
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

        JsonNode response;
        try {
            response = webClient.post()
                    .uri(baseUrl + "/v1/messages")
                    .header("x-api-key", credentials.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(LlmHttp.TIMEOUT);
        } catch (RuntimeException e) {
            throw LlmHttp.translate("anthropic", e);
        }

        StringBuilder text = new StringBuilder();
        if (response != null) {
            for (JsonNode block : response.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText(""));
                }
            }
        }
        if (text.toString().isBlank()) {
            throw new LlmProviderException("anthropic", "Empty response from Claude");
        }
        return text.toString().trim();
    }
}
