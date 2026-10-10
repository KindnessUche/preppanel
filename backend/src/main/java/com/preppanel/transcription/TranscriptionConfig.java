package com.preppanel.transcription;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class TranscriptionConfig {

    /** No default auth header - the key is per-user and added per call. */
    @Bean
    public WebClient assemblyAiWebClient(AssemblyAiProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
