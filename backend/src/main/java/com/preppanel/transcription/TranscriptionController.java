package com.preppanel.transcription;

import com.preppanel.exception.ApiException;
import com.preppanel.llm.RequestCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/transcribe")
@RequiredArgsConstructor
public class TranscriptionController {

    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final AssemblyAiClient assemblyAiClient;
    private final AudioMetricsCalculator metricsCalculator;
    private final RequestCredentials requestCredentials;

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
