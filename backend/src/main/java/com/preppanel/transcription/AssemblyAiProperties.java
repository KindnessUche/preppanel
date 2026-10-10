package com.preppanel.transcription;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** No API key here on purpose: the user's AssemblyAI key arrives with each request (see RequestCredentials). */
@ConfigurationProperties(prefix = "app.assemblyai")
public record AssemblyAiProperties(String baseUrl, List<String> speechModels) {
}
