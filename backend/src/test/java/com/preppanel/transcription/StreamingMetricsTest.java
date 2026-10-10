package com.preppanel.transcription;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The /metrics endpoint is a thin wrapper over the calculator; this pins the mapping it relies on. */
class StreamingMetricsTest {

    @Test
    void streamedWordsProduceSameMetricsAsUploadedOnes() {
        var request = new MetricsRequest(List.of(
                new MetricsRequest.Word("So", 0, 400),
                new MetricsRequest.Word("um", 500, 800),
                new MetricsRequest.Word("I", 3000, 3200),   // 2.2s gap -> one pause
                new MetricsRequest.Word("led", 3300, 3600)), 4.0);

        List<TranscriptWord> words = request.words().stream()
                .map(w -> new TranscriptWord(w.text(), w.startMs(), w.endMs())).toList();
        AudioMetrics m = new AudioMetricsCalculator().calculate(new TranscriptionResult("", request.durationSeconds(), words));

        assertThat(m.fillerWordCount()).isEqualTo(1);
        assertThat(m.pauseCount()).isEqualTo(1);
        assertThat(m.durationSeconds()).isEqualTo(4.0);
    }
}
