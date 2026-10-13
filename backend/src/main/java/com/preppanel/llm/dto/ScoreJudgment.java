package com.preppanel.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreJudgment(
        Integer contentScore,
        Integer structureScore,
        Integer technicalScore, // nullable - only present for technical questions
        String feedbackText,
        Boolean onTopic,
        String driftNote,
        String depth,                 // shallow | adequate | deep
        String structureNotes,
        java.util.List<String> accuracyIssues,
        java.util.List<String> strengths,
        java.util.List<String> improvements,
        String modelAnswer
) {
    public ScoreJudgment(Integer contentScore, Integer structureScore, Integer technicalScore, String feedbackText) {
        this(contentScore, structureScore, technicalScore, feedbackText, null, null, null, null, null, null, null, null);
    }

    /** The extra analysis as a map (no scores), stored as JSON on the score row. Null when there is none. */
    public java.util.Map<String, Object> detail() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (onTopic != null) m.put("onTopic", onTopic);
        if (driftNote != null && !driftNote.isBlank()) m.put("driftNote", driftNote);
        if (depth != null) m.put("depth", depth);
        if (structureNotes != null && !structureNotes.isBlank()) m.put("structureNotes", structureNotes);
        if (accuracyIssues != null && !accuracyIssues.isEmpty()) m.put("accuracyIssues", accuracyIssues);
        if (strengths != null && !strengths.isEmpty()) m.put("strengths", strengths);
        if (improvements != null && !improvements.isEmpty()) m.put("improvements", improvements);
        if (modelAnswer != null && !modelAnswer.isBlank()) m.put("modelAnswer", modelAnswer);
        return m.isEmpty() ? null : m;
    }
}
