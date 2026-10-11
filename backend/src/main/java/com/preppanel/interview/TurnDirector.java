package com.preppanel.interview;

import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.InterviewPlan;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides what the panel does next: dig deeper into the last answer, stress-test a claim, move to the
 * next topic, or wrap up. Deliberately plain Java rather than another LLM call, so the pacing is
 * predictable and unit-testable. The LLM supplies the *analysis*; this class supplies the *judgement
 * about the interview's shape*.
 *
 * Pacing rules:
 *  - At most {@link #MAX_FOLLOW_UPS_PER_TOPIC} follow-ups on any one topic.
 *  - Follow-ups share a small budget ({@link #FOLLOW_UP_BUDGET}); a follow-up is only allowed if enough
 *    turns remain to still cover every untouched topic, so probing can never starve the plan.
 *  - The introduction is never probed.
 *  - The interview always ends with one wrap-up turn.
 */
final class TurnDirector {

    static final int MAX_FOLLOW_UPS_PER_TOPIC = 2;
    static final int FOLLOW_UP_BUDGET = 3;

    private TurnDirector() {
    }

    /** One turn already asked: its kind and topic. */
    record Asked(TurnType type, String topicId) {
    }

    /** Opening + every topic + the follow-up allowance + the wrap-up. */
    static int maxTurns(InterviewPlan plan) {
        return 1 + plan.topics().size() + FOLLOW_UP_BUDGET + 1;
    }

    static TurnPlan opening() {
        return new TurnPlan(TurnType.OPENING, InterviewPlan.INTRO);
    }

    static TurnPlan wrapUp() {
        return new TurnPlan(TurnType.WRAP_UP, InterviewPlan.WRAP);
    }

    /** @param asked every turn so far, in order (non-empty); @param analysis of the candidate's latest answer */
    static TurnPlan next(InterviewPlan plan, List<Asked> asked, AnswerAnalysis analysis) {
        if (asked.isEmpty()) return opening();
        AnswerAnalysis a = analysis == null ? AnswerAnalysis.NONE : analysis;

        int used = asked.size();
        Asked last = asked.get(used - 1);
        if (last.type() == TurnType.WRAP_UP) return wrapUp();

        Set<String> covered = new HashSet<>();
        for (Asked q : asked) covered.add(q.topicId());
        List<InterviewPlan.Topic> uncovered = plan.topics().stream().filter(t -> !covered.contains(t.id())).toList();

        int budgetLeft = maxTurns(plan) - 1 - used; // minus one: the wrap-up is reserved
        if (budgetLeft <= 0) return wrapUp();

        InterviewPlan.Topic current = plan.byId(last.topicId());
        boolean canFollowUp = current != null
                && last.type() != TurnType.OPENING
                && followUpsOnCurrentTopic(asked) < MAX_FOLLOW_UPS_PER_TOPIC
                && budgetLeft > uncovered.size();

        if (canFollowUp) {
            boolean hasGap = !"none".equals(a.gap()) || Boolean.FALSE.equals(a.answeredQuestion());
            if (hasGap) {
                return new TurnPlan(TurnType.PROBE, current);
            }
            boolean alreadyChallenged = asked.stream()
                    .anyMatch(q -> q.type() == TurnType.CHALLENGE && current.id().equals(q.topicId()));
            if (a.claimToChallenge() != null && !alreadyChallenged) {
                return new TurnPlan(TurnType.CHALLENGE, current);
            }
        }

        if (!uncovered.isEmpty()) {
            return new TurnPlan(TurnType.NEW_TOPIC, uncovered.get(0));
        }
        return wrapUp();
    }

    /** Consecutive probe/challenge turns at the end of the transcript, on the same topic. */
    static int followUpsOnCurrentTopic(List<Asked> asked) {
        if (asked.isEmpty()) return 0;
        String topic = asked.get(asked.size() - 1).topicId();
        int n = 0;
        for (int i = asked.size() - 1; i >= 0; i--) {
            Asked q = asked.get(i);
            if (q.type().isFollowUp() && topic != null && topic.equals(q.topicId())) n++;
            else break;
        }
        return n;
    }
}
