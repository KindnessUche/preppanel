package com.preppanel.transcription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Word timestamps captured by the browser during live transcription. Bounded so it can't be abused. */
public record MetricsRequest(
        @NotNull @Size(max = 3000) List<@Valid Word> words,
        @DecimalMin("0") @DecimalMax("900") double durationSeconds
) {
    public record Word(
            @NotNull @Size(max = 100) String text,
            @Min(0) @Max(3_600_000) long startMs,
            @Min(0) @Max(3_600_000) long endMs
    ) {
    }
}
