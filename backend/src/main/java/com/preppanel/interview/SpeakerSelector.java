package com.preppanel.interview;

import com.preppanel.llm.PanelistRosterEntry;
import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;

import java.util.List;

/**
 * Chooses who on the panel speaks next, by need rather than rotation: the tech lead takes technical
 * gaps, the skeptic takes claims to stress-test, the hiring manager opens, and so on. Variety rules keep
 * one person from monopolising the room and make sure everyone gets a turn when the interview is long enough.
 */
final class SpeakerSelector {

    private static final double NOT_SPOKEN_YET_BONUS = 2.5;
    private static final double SPOKE_LAST_PENALTY = 4.0;
    private static final double FREQUENT_SPEAKER_PENALTY = 1.5;

    private SpeakerSelector() {
    }

    /** @param priorSpeakers display names of whoever asked each earlier question, oldest first */
    static PanelistRosterEntry select(
            List<PanelistRosterEntry> roster, TurnPlan plan, AnswerAnalysis analysis, List<String> priorSpeakers) {
        if (roster.size() == 1) return roster.get(0);
        AnswerAnalysis a = analysis == null ? AnswerAnalysis.NONE : analysis;

        PanelistRosterEntry best = roster.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PanelistRosterEntry candidate : roster) {
            double score = affinity(candidate.archetype().id(), plan, a) + variety(candidate.name(), priorSpeakers);
            if (score > bestScore) { // strict > : ties go to whoever was cast first
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** How naturally this archetype fits this kind of turn. */
    static double affinity(String archetypeId, TurnPlan plan, AnswerAnalysis a) {
        TurnType type = plan.type();
        String lens = plan.topic() == null ? "" : plan.topic().lens();
        String gap = a.gap();
        double s = 0;
        switch (archetypeId) {
            case "hiring-manager" -> {
                if (type == TurnType.OPENING) s += 10;
                if (type == TurnType.WRAP_UP) s += 3;
                if (lens.equals("motivation") || lens.equals("growth")) s += 3;
                if (lens.equals("leadership")) s += 1;
                if (type == TurnType.PROBE && gap.equals("ownership")) s += 1;
            }
            case "tech-lead" -> {
                if (lens.equals("technical")) s += 4;
                if (type == TurnType.PROBE && gap.equals("technical_depth")) s += 4;
                if (type.isFollowUp()) s += 0.5;
            }
            case "skeptic" -> {
                if (type == TurnType.CHALLENGE) s += 6;
                if (type == TurnType.PROBE && gap.equals("reasoning")) s += 3;
                if (type == TurnType.PROBE) s += 1;
            }
            case "bar-raiser" -> {
                if (type == TurnType.WRAP_UP) s += 4;
                if (type == TurnType.PROBE && gap.equals("result")) s += 3;
                if (type == TurnType.PROBE && gap.equals("specifics")) s += 2;
                if (lens.equals("judgment") || lens.equals("ownership")) s += 2;
            }
            case "peer" -> {
                if (lens.equals("collaboration")) s += 4;
                if (type == TurnType.OPENING) s += 4; // a friendly second choice for opening the room
                if (type == TurnType.NEW_TOPIC) s += 0.5;
                if (type == TurnType.WRAP_UP) s += 1; // a friendly goodbye when the usual closers just spoke
            }
            default -> {
            }
        }
        return s;
    }

    private static double variety(String name, List<String> priorSpeakers) {
        double v = 0;
        if (!priorSpeakers.contains(name)) v += NOT_SPOKEN_YET_BONUS;
        if (!priorSpeakers.isEmpty() && name.equals(priorSpeakers.get(priorSpeakers.size() - 1))) v -= SPOKE_LAST_PENALTY;
        int from = Math.max(0, priorSpeakers.size() - 3);
        long recent = priorSpeakers.subList(from, priorSpeakers.size()).stream().filter(name::equals).count();
        if (recent >= 2) v -= FREQUENT_SPEAKER_PENALTY;
        return v;
    }
}
