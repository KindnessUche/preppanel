package com.preppanel.transcription;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Delivery metrics derived from word timestamps. Stored as JSON in answers.audio_metrics.
 * The bounds make this safe to accept back from the client on answer submission.
 */
public record AudioMetrics(
        @Min(0) @Max(500) int wordsPerMinute,
        @Min(0) @Max(500) int fillerWordCount,
        @Min(0) @Max(500) int pauseCount,
        @DecimalMin("0") @DecimalMax("900") double durationSeconds
) {
}
