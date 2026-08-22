package com.preppanel.llm;

import com.preppanel.llm.dto.GeminiGenerateRequest;
import com.preppanel.llm.dto.GeminiGenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiLlmClient implements LlmClientService {

    private final WebClient geminiWebClient;
    private final LlmProperties properties;

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String model = properties.gemini().model();
        String apiKey = properties.gemini().apiKey();

        GeminiGenerateResponse response = geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .bodyValue(GeminiGenerateRequest.of(systemPrompt, userPrompt))
                .retrieve()
                .bodyToMono(GeminiGenerateResponse.class)
                .block(Duration.ofSeconds(15));

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new LlmProviderException("gemini", "Empty response from Gemini");
        }

        String text = response.candidates().get(0).content().parts().get(0).text();
        if (text == null || text.isBlank()) {
            throw new LlmProviderException("gemini", "Blank content in Gemini response");
        }

        return text.trim();
    }
}
