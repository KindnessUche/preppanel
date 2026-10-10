package com.preppanel.transcription;

/** One recognized word with AssemblyAI's millisecond timestamps. */
public record TranscriptWord(String text, long startMs, long endMs) {
}
