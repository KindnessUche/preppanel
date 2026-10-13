package com.preppanel.interview;

import com.preppanel.interview.TurnDirector.Asked;
import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.InterviewPlan;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.preppanel.llm.dto.TurnType.*;
import static org.assertj.core.api.Assertions.assertThat;

class TurnDirectorTest {

    private final InterviewPlan plan = InterviewPlan.defaultPlan(4); // t1..t4

    private static final AnswerAnalysis VAGUE =
            new AnswerAnalysis(null, "result", "They never said the outcome.", null, true, false);
    private static final AnswerAnalysis CLAIM =
            new AnswerAnalysis(null, "none", null, "Cut latency by 90 percent", true, false);
    private static final AnswerAnalysis FINE = AnswerAnalysis.NONE;

    private static Asked a(TurnType type, String topic) {
        return new Asked(type, topic);
    }

    @Test
    void firstTurnIsTheOpening() {
        assertThat(TurnDirector.next(plan, List.of(), FINE).type()).isEqualTo(OPENING);
    }

    @Test
    void afterTheOpening_movesToTheFirstTopic_andNeverProbesTheIntroduction() {
        TurnPlan next = TurnDirector.next(plan, List.of(a(OPENING, "intro")), VAGUE);

        assertThat(next.type()).isEqualTo(NEW_TOPIC);
        assertThat(next.topic().id()).isEqualTo("t1");
    }

    @Test
    void aGapInTheAnswer_isProbedOnTheSameTopic() {
        TurnPlan next = TurnDirector.next(plan, List.of(a(OPENING, "intro"), a(NEW_TOPIC, "t1")), VAGUE);

        assertThat(next.type()).isEqualTo(PROBE);
        assertThat(next.topic().id()).isEqualTo("t1");
    }

    @Test
    void aSidesteppedQuestion_isRedirectedEvenWithNoNamedGap() {
        AnswerAnalysis dodged = new AnswerAnalysis(null, "none", null, null, false, false);

        assertThat(TurnDirector.next(plan, List.of(a(OPENING, "intro"), a(NEW_TOPIC, "t1")), dodged).type()).isEqualTo(com.preppanel.llm.dto.TurnType.REDIRECT);
    }

    @Test
    void aSolidAnswer_movesOnToTheNextUncoveredTopic() {
        TurnPlan next = TurnDirector.next(plan, List.of(a(OPENING, "intro"), a(NEW_TOPIC, "t1")), FINE);

        assertThat(next.type()).isEqualTo(NEW_TOPIC);
        assertThat(next.topic().id()).isEqualTo("t2");
    }

    @Test
    void aBoldClaimWithNoGap_isChallenged_butOnlyOncePerTopic() {
        List<Asked> afterFirst = List.of(a(OPENING, "intro"), a(NEW_TOPIC, "t1"));
        assertThat(TurnDirector.next(plan, afterFirst, CLAIM).type()).isEqualTo(CHALLENGE);

        List<Asked> afterChallenge = List.of(a(OPENING, "intro"), a(NEW_TOPIC, "t1"), a(CHALLENGE, "t1"));
        TurnPlan next = TurnDirector.next(plan, afterChallenge, CLAIM);
        assertThat(next.type()).isEqualTo(NEW_TOPIC);
        assertThat(next.topic().id()).isEqualTo("t2");
    }

    @Test
    void aGapIsProbedBeforeAClaimIsChallenged() {
        AnswerAnalysis both = new AnswerAnalysis(null, "ownership", "Unclear what they did.", "Led a 40-person org", true, false);

        assertThat(TurnDirector.next(plan, List.of(a(OPENING, "intro"), a(NEW_TOPIC, "t1")), both).type()).isEqualTo(PROBE);
    }

    @Test
    void atMostTwoFollowUpsOnOneTopic() {
        List<Asked> asked = List.of(a(OPENING, "intro"), a(NEW_TOPIC, "t1"), a(PROBE, "t1"), a(PROBE, "t1"));

        TurnPlan next = TurnDirector.next(plan, asked, VAGUE);

        assertThat(next.type()).isEqualTo(NEW_TOPIC);
        assertThat(next.topic().id()).isEqualTo("t2");
    }

    @Test
    void followUpsNeverStarveUntouchedTopics() {
        // 7 turns used of a 9-turn interview, 1 reserved for the wrap-up: one turn left, and t4 still untouched.
        List<Asked> asked = List.of(a(OPENING, "intro"),
                a(NEW_TOPIC, "t1"), a(PROBE, "t1"),
                a(NEW_TOPIC, "t2"), a(PROBE, "t2"),
                a(NEW_TOPIC, "t3"), a(PROBE, "t3"));

        TurnPlan next = TurnDirector.next(plan, asked, VAGUE);

        assertThat(next.type()).isEqualTo(NEW_TOPIC);
        assertThat(next.topic().id()).isEqualTo("t4");
    }

    @Test
    void whenEveryTopicIsCovered_andThereIsBudget_aGapIsStillProbed_otherwiseWrapUp() {
        List<Asked> covered = List.of(a(OPENING, "intro"),
                a(NEW_TOPIC, "t1"), a(NEW_TOPIC, "t2"), a(NEW_TOPIC, "t3"), a(NEW_TOPIC, "t4"));

        assertThat(TurnDirector.next(plan, covered, VAGUE).type()).isEqualTo(PROBE);
        assertThat(TurnDirector.next(plan, covered, FINE).type()).isEqualTo(WRAP_UP);
    }

    @Test
    void whenTheTurnBudgetIsSpent_itWrapsUpEvenWithTopicsLeft() {
        // maxTurns = 9; 8 used means no turn left before the reserved wrap-up.
        List<Asked> asked = List.of(a(OPENING, "intro"),
                a(NEW_TOPIC, "t1"), a(PROBE, "t1"), a(PROBE, "t1"),
                a(NEW_TOPIC, "t2"), a(PROBE, "t2"),
                a(NEW_TOPIC, "t3"), a(NEW_TOPIC, "bank-8"));

        assertThat(TurnDirector.next(plan, asked, FINE).type()).isEqualTo(WRAP_UP);
    }

    @Test
    void anInterviewAlwaysTerminates_andEndsWithExactlyOneWrapUp() {
        // Worst case: every answer has a gap. Drive the director until it wraps up.
        java.util.ArrayList<Asked> asked = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            TurnPlan next = TurnDirector.next(plan, asked, VAGUE);
            asked.add(new Asked(next.type(), next.topic().id()));
            if (next.type() == WRAP_UP) break;
        }

        assertThat(asked.get(asked.size() - 1).type()).isEqualTo(WRAP_UP);
        assertThat(asked.size()).isLessThanOrEqualTo(TurnDirector.maxTurns(plan));
        assertThat(asked.stream().filter(q -> q.type() == WRAP_UP).count()).isEqualTo(1);
        // ...and even then, every topic got asked.
        assertThat(asked.stream().filter(q -> q.type() == NEW_TOPIC).map(Asked::topicId).distinct().count()).isEqualTo(4);
    }

    @Test
    void countsOnlyTrailingFollowUpsOnTheCurrentTopic() {
        assertThat(TurnDirector.followUpsOnCurrentTopic(List.of(a(NEW_TOPIC, "t1"), a(PROBE, "t1"), a(CHALLENGE, "t1")))).isEqualTo(2);
        assertThat(TurnDirector.followUpsOnCurrentTopic(List.of(a(PROBE, "t1"), a(NEW_TOPIC, "t2")))).isZero();
    }
}
