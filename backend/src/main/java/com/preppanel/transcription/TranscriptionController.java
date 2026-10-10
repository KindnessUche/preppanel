package com.preppanel.transcription;

import com.preppanel.exception.ApiException;
import com.preppanel.llm.RequestCredentials;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/transcribe")
@RequiredArgsConstructor
public class TranscriptionController {

    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final AssemblyAiClient assemblyAiClient;
    private final AudioMetricsCalculator metricsCalculator;
    private final RequestCredentials requestCredentials;

    /** Optional streaming model override (empty = AssemblyAI's default). See application.yml. */
    @Value("${app.assemblyai.streaming-speech-model:}")
    private String streamingSpeechModel;

    /**
     * Live transcription: hands the browser a short-lived token (never the real key) plus where to connect.
     * The browser then streams microphone audio straight to AssemblyAI and gets words back as they're spoken.
     */
    @GetMapping("/token")
    public ResponseEntity<StreamingTokenResponse> streamingToken() {
        String apiKey = requestCredentials.assemblyAiKey().orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST, "Add your AssemblyAI API key in Account → API keys to answer by voice."));
        String token = assemblyAiClient.createStreamingToken(apiKey);
        String model = streamingSpeechModel == null || streamingSpeechModel.isBlank() ? null : streamingSpeechModel.trim();
        return ResponseEntity.ok(new StreamingTokenResponse(token, AssemblyAiClient.STREAMING_WS_URL, model));
    }

    /**
     * Delivery metrics for a live-transcribed answer: the browser sends the word timestamps it received
     * and gets the same words/min, filler and pause figures the upload path produces. No third-party call.
     */
    @PostMapping(value = "/metrics", consumes = "application/json")
    public ResponseEntity<AudioMetrics> metrics(@Valid @RequestBody MetricsRequest request) {
        List<TranscriptWord> words = request.words().stream()
                .map(w -> new TranscriptWord(w.text(), w.startMs(), w.endMs()))
                .toList();
        return ResponseEntity.ok(metricsCalculator.calculate(new TranscriptionResult("", request.durationSeconds(), words)));
    }

    /** Requires auth (anyRequest().authenticated()). Returns the transcript + delivery metrics. */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<TranscriptionResponse> transcribe(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No audio uploaded");
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recording must be 10MB or smaller");
        }
        String type = file.getContentType();
        // Browsers record audio/webm (Chrome/Firefox) or audio/mp4 (Safari); webm may arrive as video/webm.
        if (type == null || !(type.startsWith("audio/") || type.startsWith("video/webm"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported audio format");
        }

        String apiKey = requestCredentials.assemblyAiKey().orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST, "Add your AssemblyAI API key in Account → API keys to answer by voice."));

        TranscriptionResult result = assemblyAiClient.transcribe(file.getBytes(), apiKey);
        if (result.text() == null || result.text().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "No speech detected - try again closer to the mic");
        }
        return ResponseEntity.ok(new TranscriptionResponse(result.text(), metricsCalculator.calculate(result)));
    }
}
