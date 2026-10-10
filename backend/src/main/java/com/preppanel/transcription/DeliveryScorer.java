package com.preppanel.transcription;

import org.springframework.stereotype.Component;

/**
 * Turns delivery metrics into a 0-100 score. Deliberately a transparent heuristic, not an
 * LLM call: a comfortable pace (110-170 wpm), few fillers per minute, and few long pauses.
 */
@Component
public class DeliveryScorer {

    static final int IDEAL_MIN_WPM = 110;
    static final int IDEAL_MAX_WPM = 170;

    public int score(AudioMetrics m) {
        double score = 100;

        if (m.wordsPerMinute() > 0) {
            if (m.wordsPerMinute() < IDEAL_MIN_WPM) {
                score -= Math.min(35, (IDEAL_MIN_WPM - m.wordsPerMinute()) * 0.6);
            } else if (m.wordsPerMinute() > IDEAL_MAX_WPM) {
                score -= Math.min(35, (m.wordsPerMinute() - IDEAL_MAX_WPM) * 0.6);
            }
        }

        double minutes = Math.max(m.durationSeconds(), 5) / 60.0; // floor avoids huge rates on tiny clips
        double fillersPerMinute = m.fillerWordCount() / minutes;
        score -= Math.min(30, fillersPerMinute * 5);

        score -= Math.min(15, Math.max(0, m.pauseCount() - 2) * 3);

        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }
}
