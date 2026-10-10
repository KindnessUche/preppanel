package com.preppanel.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.preppanel.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pre-recorded transcription flow: upload audio -> create transcript -> poll until done.
 * Blocking on purpose (same as the LLM clients) since the rest of the service layer is synchronous.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AssemblyAiClient {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(1500);
    private static final Duration MAX_WAIT = Duration.ofSeconds(90);

    /** Streaming lives on its own host, separate from the pre-recorded API (api.assemblyai.com). */
    static final String STREAMING_TOKEN_URL = "https://streaming.assemblyai.com/v3/token";
    public static final String STREAMING_WS_URL = "wss://streaming.assemblyai.com/v3/ws";

    private final WebClient assemblyAiWebClient;
    private final AssemblyAiProperties properties;

    /**
     * Exchanges the user's real API key for a short-lived, single-purpose token the browser can use to open
     * the live-transcription WebSocket. Browsers can't set auth headers on a WebSocket, and we never want the
     * real key in the page, so this is AssemblyAI's recommended pattern.
     */
    public String createStreamingToken(String apiKey) {
        try {
            JsonNode body = assemblyAiWebClient.get()
                    .uri(URI.create(STREAMING_TOKEN_URL + "?expires_in_seconds=120"))
                    .header("authorization", apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(15));
            if (body == null || !body.hasNonNull("token")) {
                throw new IllegalStateException("AssemblyAI returned no streaming token");
            }
            return body.get("token").asText();
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "AssemblyAI rejected your API key. Check it in Account → API keys.");
            }
            log.warn("AssemblyAI streaming token request failed with HTTP {}: {}", status, e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Live transcription isn't available on your AssemblyAI account (HTTP " + status + "). You can still type your answer.");
        } catch (Exception e) {
            log.warn("AssemblyAI streaming token failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not start live transcription - you can still type your answer.");
        }
    }

    public TranscriptionResult transcribe(byte[] audio, String apiKey) {
        try {
            String uploadUrl = upload(audio, apiKey);
            String transcriptId = createTranscript(uploadUrl, apiKey);
            return awaitResult(transcriptId, apiKey);
        } catch (ApiException e) {
            throw e;
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                // 422, not 401: the frontend treats 401 as "login expired" and would retry.
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "AssemblyAI rejected your API key. Check it in Account → API keys.");
            }
            log.warn("AssemblyAI request failed with HTTP {}", status);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Transcription failed - please try again or type your answer");
        } catch (Exception e) {
            log.warn("AssemblyAI transcription failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Transcription failed - please try again or type your answer");
        }
    }

    private String upload(byte[] audio, String apiKey) {
        JsonNode body = assemblyAiWebClient.post()
                .uri("/v2/upload")
                .header("authorization", apiKey)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(audio)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(30));
        if (body == null || !body.hasNonNull("upload_url")) {
            throw new IllegalStateException("AssemblyAI upload returned no upload_url");
        }
        return body.get("upload_url").asText();
    }

    private String createTranscript(String uploadUrl, String apiKey) {
        JsonNode body = assemblyAiWebClient.post()
                .uri("/v2/transcript")
                .header("authorization", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "audio_url", uploadUrl,
                        "speech_models", properties.speechModels(),
                        // Keep "um"/"uh" in the transcript - needed for filler-word metrics.
                        "disfluencies", true
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(30));
        if (body == null || !body.hasNonNull("id")) {
            throw new IllegalStateException("AssemblyAI create-transcript returned no id");
        }
        return body.get("id").asText();
    }

    private TranscriptionResult awaitResult(String transcriptId, String apiKey) throws InterruptedException {
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode body = assemblyAiWebClient.get()
                    .uri("/v2/transcript/{id}", transcriptId)
                    .header("authorization", apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));
            String status = body == null ? "" : body.path("status").asText();

            if ("completed".equals(status)) {
                return parse(body);
            }
            if ("error".equals(status)) {
                throw new IllegalStateException("AssemblyAI error: " + body.path("error").asText());
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Transcription timed out - please try a shorter recording");
    }

    static TranscriptionResult parse(JsonNode body) {
        List<TranscriptWord> words = new ArrayList<>();
        for (JsonNode w : body.path("words")) {
            words.add(new TranscriptWord(w.path("text").asText(), w.path("start").asLong(), w.path("end").asLong()));
        }
        return new TranscriptionResult(body.path("text").asText(""), body.path("audio_duration").asDouble(0), words);
    }
}
