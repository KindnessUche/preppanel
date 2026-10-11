package com.preppanel.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What the panel intends to cover. Immutable once created; which topics have been covered is derived
 * from the questions already asked, so there is no mutable plan state to keep in sync.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InterviewPlan(List<Topic> topics) {

    public static final Set<String> LENSES =
            Set.of("technical", "collaboration", "ownership", "judgment", "growth", "motivation", "leadership");

    public static final String INTRO_ID = "intro";
    public static final String WRAP_ID = "wrap-up";

    // Lens "general" is deliberately not one of LENSES: the opening and close favour no panelist's specialty.
    public static final Topic INTRO = new Topic(INTRO_ID, "Introduction", "general",
            "Their background and what draws them to the role.", null);
    public static final Topic WRAP = new Topic(WRAP_ID, "Wrap-up", "general", "Closing the interview.", null);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Topic(String id, String label, String lens, String focus, String resumeHook) {
    }

    public Topic byId(String id) {
        if (id == null || topics == null) return null;
        return topics.stream().filter(t -> id.equals(t.id())).findFirst().orElse(null);
    }

    /**
     * Validates model output. Returns null if it's unusable (caller then uses {@link #defaultPlan}).
     * Ids are assigned here (t1, t2, ...) rather than trusted from the model.
     */
    public static InterviewPlan sanitize(InterviewPlan raw, int expectedCount) {
        if (raw == null || raw.topics() == null) return null;
        List<Topic> cleaned = new ArrayList<>();
        for (Topic t : raw.topics()) {
            if (t == null || t.label() == null || t.label().isBlank()) continue;
            String lens = t.lens() == null ? "" : t.lens().trim().toLowerCase();
            if (!LENSES.contains(lens)) lens = "ownership";
            cleaned.add(new Topic(
                    "t" + (cleaned.size() + 1),
                    cut(t.label(), 100),
                    lens,
                    t.focus() == null || t.focus().isBlank() ? "A specific past example with a clear outcome." : cut(t.focus(), 300),
                    blankToNull(t.resumeHook())));
            if (cleaned.size() == Math.max(expectedCount, 3) + 1) break;
        }
        return cleaned.size() < 3 ? null : new InterviewPlan(List.copyOf(cleaned));
    }

    /** Sensible, role-agnostic topics for when plan generation fails. Order matters: easier first. */
    public static InterviewPlan defaultPlan(int count) {
        List<Topic> all = List.of(
                new Topic("t1", "Owning a piece of work end to end", "ownership",
                        "A project they drove personally, with a concrete outcome.", null),
                new Topic("t2", "Working through a disagreement", "collaboration",
                        "A real conflict with a colleague and how it was resolved.", null),
                new Topic("t3", "A technical decision they owned", "technical",
                        "A decision with real trade-offs, and why they chose what they did.", null),
                new Topic("t4", "A failure and what changed afterwards", "growth",
                        "Something that went wrong, their part in it, and what they do differently now.", null),
                new Topic("t5", "Prioritising under pressure", "judgment",
                        "Competing demands, how they chose, and what they gave up.", null));
        return new InterviewPlan(all.subList(0, Math.max(3, Math.min(count, all.size()))));
    }

    private static String cut(String s, int max) {
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank() || s.trim().equalsIgnoreCase("null")) return null;
        return cut(s, 300);
    }
}
