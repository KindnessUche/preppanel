package com.preppanel.transcription;

import java.util.List;

public record TranscriptionResult(String text, double audioDurationSeconds, List<TranscriptWord> words) {
}
