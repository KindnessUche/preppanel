package com.preppanel.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

/**
 * The hidden assessor's private read on one answer. Drives the next move (probe / challenge / move on)
 * and gives the speaking panelist something concrete to react to. The candidate never sees this.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerAnalysis(
        String standout,          // the most concrete/notable thing they said, paraphrased; null if nothing stood out
        String gap,               // none | specifics | ownership | result | technical_depth | reasoning
        String gapDetail,         // one sentence on exactly what is missing; null when gap is none
        String claimToChallenge,  // a claim worth stress-testing, or null
        Boolean answeredQuestion, // false if they dodged or went off-topic
        Boolean rambling
) {
    public static final Set<String> GAPS = Set.of("none", "specifics", "ownership", "result", "technical_depth", "reasoning");

    /** "Nothing to follow up" - used when the assessor fails, so the interview just moves on. */
    public static final AnswerAnalysis NONE = new AnswerAnalysis(null, "none", null, null, true, false);

    /** Cleans up model output: unknown gap -> none, blanks -> null, missing flags -> sensible defaults. */
    public AnswerAnalysis normalized() {
        String g = gap == null ? "none" : gap.trim().toLowerCase();
        if (!GAPS.contains(g)) g = "none";
        String detail = clean(gapDetail, 240);
        if (!"none".equals(g) && detail == null) {
            detail = switch (g) {
                case "specifics" -> "The answer stayed generic, with no concrete example.";
                case "ownership" -> "It's unclear what the candidate personally did.";
                case "result" -> "They never said what the outcome was.";
                case "technical_depth" -> "A technical claim was made without saying how it worked.";
                default -> "They didn't explain why they chose that approach.";
            };
        }
        return new AnswerAnalysis(
                clean(standout, 200), g, "none".equals(g) ? null : detail, clean(claimToChallenge, 300),
                answeredQuestion == null || answeredQuestion, Boolean.TRUE.equals(rambling));
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("null")) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
