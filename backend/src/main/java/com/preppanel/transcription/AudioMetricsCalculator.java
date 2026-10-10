package com.preppanel.transcription;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure backend logic (no extra API): words/min, filler words and long pauses,
 * computed from the transcript's word timestamps.
 */
@Component
public class AudioMetricsCalculator {

    /** A silence gap longer than this between two words counts as a "pause". */
    static final long PAUSE_THRESHOLD_MS = 1200;

    private static final Set<String> SINGLE_WORD_FILLERS = Set.of("um", "uh", "umm", "uhh", "er", "erm", "ah", "hmm");

    public AudioMetrics calculate(TranscriptionResult result) {
        List<TranscriptWord> words = result.words() == null ? List.of() : result.words();
        if (words.isEmpty()) {
            return new AudioMetrics(0, 0, 0, result.audioDurationSeconds());
        }

        // Speaking time = first word start to last word end (ignores leading/trailing silence,
        // so a 5s pause before answering doesn't tank the words-per-minute figure).
        long speakingMs = Math.max(1, words.get(words.size() - 1).endMs() - words.get(0).startMs());
        int wpm = (int) Math.round(words.size() / (speakingMs / 60000.0));

        int fillers = 0;
        for (int i = 0; i < words.size(); i++) {
            String w = normalize(words.get(i).text());
            if (SINGLE_WORD_FILLERS.contains(w)) {
                fillers++;
            } else if (w.equals("you") && i + 1 < words.size() && normalize(words.get(i + 1).text()).equals("know")) {
                fillers++; // "you know" as a two-word filler
            }
        }

        int pauses = 0;
        for (int i = 1; i < words.size(); i++) {
            if (words.get(i).startMs() - words.get(i - 1).endMs() > PAUSE_THRESHOLD_MS) {
                pauses++;
            }
        }

        return new AudioMetrics(wpm, fillers, pauses, result.audioDurationSeconds());
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}
