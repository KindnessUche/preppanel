package com.preppanel.llm;

/** Models sometimes wrap JSON in markdown fences or add a sentence around it. This pulls out the object. */
public final class LlmJson {

    private LlmJson() {
    }

    public static String extractObject(String raw) {
        if (raw == null) throw new IllegalArgumentException("Empty model response");
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("No JSON object in model response");
        return raw.substring(start, end + 1);
    }
}
