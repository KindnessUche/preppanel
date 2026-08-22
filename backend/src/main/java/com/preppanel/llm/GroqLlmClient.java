package com.preppanel.llm;

import com.preppanel.llm.dto.ChatCompletionRequest;
import com.preppanel.llm.dto.ChatCompletionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class GroqLlmClient implements LlmClientService {

    private final WebClient groqWebClient;
    private final LlmProperties properties;

    @Override
    public String providerName() {
        return "groq";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.groq().model(),
                List.of(
                        new ChatCompletionRequest.Message("system", systemPrompt),
                        new ChatCompletionRequest.Message("user", userPrompt)
                ),
                0.9
        );

        ChatCompletionResponse response = groqWebClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                // Blocking call: InterviewService is a synchronous @Transactional service,
                // so we block here rather than threading Mono/reactive types through JPA calls.
                // A short timeout keeps a slow/stuck request from hanging the request thread.
                .block(Duration.ofSeconds(15));

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmProviderException("groq", "Empty response from Groq");
        }

        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            throw new LlmProviderException("groq", "Blank content in Groq response");
        }

        return content.trim();
    }
}
