package com.preppanel.llm.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPlanTest {

    private static InterviewPlan.Topic topic(String label, String lens) {
        return new InterviewPlan.Topic("model-chose-this-id", label, lens, "focus", null);
    }

    @Test
    void sanitize_assignsOwnIds_normalisesLenses_andDropsBlankTopics() {
        InterviewPlan raw = new InterviewPlan(Arrays.asList(
                topic("Owning an outage", "OWNERSHIP"),
                topic("   ", "technical"),
                null,
                topic("A design call", "made-up-lens"),
                topic("A miss", "growth"),
                topic("A conflict", "collaboration")));

        InterviewPlan clean = InterviewPlan.sanitize(raw, 4);

        assertThat(clean.topics()).extracting(InterviewPlan.Topic::id).containsExactly("t1", "t2", "t3", "t4");
        assertThat(clean.topics()).extracting(InterviewPlan.Topic::lens)
                .containsExactly("ownership", "ownership", "growth", "collaboration"); // unknown lens -> ownership
    }

    @Test
    void sanitize_rejectsPlansWithTooFewUsableTopics() {
        assertThat(InterviewPlan.sanitize(new InterviewPlan(List.of(topic("Only one", "growth"))), 4)).isNull();
        assertThat(InterviewPlan.sanitize(null, 4)).isNull();
        assertThat(InterviewPlan.sanitize(new InterviewPlan(null), 4)).isNull();
    }

    @Test
    void sanitize_turnsNullishResumeHooksIntoNull() {
        InterviewPlan.Topic withHook = new InterviewPlan.Topic("x", "A", "growth", "f", "null");
        InterviewPlan clean = InterviewPlan.sanitize(new InterviewPlan(List.of(withHook, topic("B", "growth"), topic("C", "technical"))), 3);

        assertThat(clean.topics().get(0).resumeHook()).isNull();
    }

    @Test
    void defaultPlan_hasTheRequestedNumberOfTopics_andSomeTechnicalContent() {
        assertThat(InterviewPlan.defaultPlan(4).topics()).hasSize(4);
        assertThat(InterviewPlan.defaultPlan(5).topics()).hasSize(5);
        assertThat(InterviewPlan.defaultPlan(4).topics()).anyMatch(t -> t.lens().equals("technical"));
        assertThat(InterviewPlan.defaultPlan(99).topics()).hasSize(5);
    }

    @Test
    void byId_findsTopics_andReturnsNullForIntroAndUnknown() {
        InterviewPlan plan = InterviewPlan.defaultPlan(4);

        assertThat(plan.byId("t2").label()).isNotBlank();
        assertThat(plan.byId("intro")).isNull();
        assertThat(plan.byId(null)).isNull();
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InterviewPlan plan = InterviewPlan.defaultPlan(4);

        InterviewPlan back = mapper.readValue(mapper.writeValueAsString(plan), InterviewPlan.class);

        assertThat(back).isEqualTo(plan);
    }

    @Test
    void answerAnalysis_normalizesModelOutput() {
        AnswerAnalysis messy = new AnswerAnalysis("  ", "Totally-Unknown", "x", "null", null, null);
        AnswerAnalysis clean = messy.normalized();

        assertThat(clean.gap()).isEqualTo("none");
        assertThat(clean.gapDetail()).isNull();
        assertThat(clean.standout()).isNull();
        assertThat(clean.claimToChallenge()).isNull();
        assertThat(clean.answeredQuestion()).isTrue();
        assertThat(clean.rambling()).isFalse();
    }

    @Test
    void answerAnalysis_fillsInAGapDetail_whenTheModelOmitsIt() {
        AnswerAnalysis clean = new AnswerAnalysis(null, "RESULT", null, null, true, false).normalized();

        assertThat(clean.gap()).isEqualTo("result");
        assertThat(clean.gapDetail()).contains("outcome");
    }

    @Test
    void turnType_roundTripsAndDefaultsToATopicQuestion() {
        for (TurnType t : TurnType.values()) assertThat(TurnType.fromDb(t.db())).isEqualTo(t);
        assertThat(TurnType.fromDb(null)).isEqualTo(TurnType.NEW_TOPIC);
        assertThat(TurnType.fromDb("garbage")).isEqualTo(TurnType.NEW_TOPIC);
    }
}
