package com.preppanel.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioMetricsTest {

    private final AudioMetricsCalculator calculator = new AudioMetricsCalculator();
    private final DeliveryScorer scorer = new DeliveryScorer();

    private TranscriptWord w(String text, long start, long end) {
        return new TranscriptWord(text, start, end);
    }

    @Test
    void calculate_countsFillersPausesAndWpm() {
        // 10 words over 6 seconds of speaking = 100 wpm; "um", "uh", "you know" = 3 fillers; one 2s gap = 1 pause
        List<TranscriptWord> words = List.of(
                w("So", 0, 400), w("um", 500, 800), w("I", 900, 1100), w("led", 1200, 1600),
                w("a", 1700, 1800), w("team", 1900, 2400), w("uh", 4400, 4700), w("you", 4800, 5000),
                w("know", 5000, 5300), w("Okay.", 5500, 6000));

        AudioMetrics m = calculator.calculate(new TranscriptionResult("...", 6.0, words));

        assertThat(m.wordsPerMinute()).isEqualTo(100);
        assertThat(m.fillerWordCount()).isEqualTo(3);
        assertThat(m.pauseCount()).isEqualTo(1);
    }

    @Test
    void calculate_noWords_returnsZeros_withoutDividingByZero() {
        AudioMetrics m = calculator.calculate(new TranscriptionResult("", 3.0, List.of()));
        assertThat(m.wordsPerMinute()).isZero();
        assertThat(m.fillerWordCount()).isZero();
    }

    @Test
    void deliveryScore_goodPaceNoFillers_isPerfect() {
        assertThat(scorer.score(new AudioMetrics(140, 0, 1, 60))).isEqualTo(100);
    }

    @Test
    void deliveryScore_penalizesRushingFillersAndPauses_butStaysInRange() {
        int bad = scorer.score(new AudioMetrics(260, 30, 20, 60));
        assertThat(bad).isBetween(0, 50);
        assertThat(bad).isLessThan(scorer.score(new AudioMetrics(140, 0, 0, 60)));
    }

    @Test
    void parse_readsAssemblyAiResponseShape() throws Exception {
        String json = "{\"status\":\"completed\",\"text\":\"Hello there\",\"audio_duration\":2.5,"
                + "\"words\":[{\"text\":\"Hello\",\"start\":100,\"end\":500},{\"text\":\"there\",\"start\":600,\"end\":900}]}";
        TranscriptionResult r = AssemblyAiClient.parse(new ObjectMapper().readTree(json));
        assertThat(r.text()).isEqualTo("Hello there");
        assertThat(r.audioDurationSeconds()).isEqualTo(2.5);
        assertThat(r.words()).hasSize(2);
        assertThat(r.words().get(1).startMs()).isEqualTo(600);
    }
}
