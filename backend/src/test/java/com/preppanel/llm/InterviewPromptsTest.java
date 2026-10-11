package com.preppanel.llm;

import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.InterviewPlan;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;
import com.preppanel.panel.PanelistArchetype;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The prompt text is the product here. These pin the behaviours it is supposed to produce. */
class InterviewPromptsTest {

    private final PanelistRosterEntry sam = new PanelistRosterEntry("Sam", PanelistArchetype.byId("tech-lead"));
    private final PanelistRosterEntry priya = new PanelistRosterEntry("Priya", PanelistArchetype.byId("hiring-manager"));
    private final List<PanelistRosterEntry> panel = List.of(sam, priya);

    private final InterviewPlan.Topic topic = new InterviewPlan.Topic(
            "t1", "Owning an outage", "ownership", "A real incident they led", "the payments migration at Acme");

    private final List<TranscriptTurn> prior = List.of(
            new TranscriptTurn("Priya", "Walk us through your background.", "I'm a backend engineer.", "opening"),
            new TranscriptTurn("Sam", "Tell me about an outage you owned.", "We rebuilt the pipeline.", "new_topic"));

    private InterviewPrompts.Prompt turn(TurnPlan plan, AnswerAnalysis analysis, String previous) {
        return InterviewPrompts.turn("Backend Engineer", "mid", sam, panel, prior, "Six years at Acme.", plan, analysis, previous);
    }

    @Test
    void noPromptEverLeaksAnUnfilledPlaceholder() {
        AnswerAnalysis gap = new AnswerAnalysis("rebuilt the pipeline", "result", "No outcome was given.", "Cut cost 80 percent", true, false);
        for (TurnType type : TurnType.values()) {
            InterviewPrompts.Prompt p = turn(new TurnPlan(type, topic), gap, "Priya");
            assertThat(p.system()).doesNotContain("%s").doesNotContain("%d");
            assertThat(p.user()).doesNotContain("%s");
        }
        assertThat(InterviewPrompts.plan("Nurse", null, null, panel, 4).system()).doesNotContain("%d").contains("exactly 4 topics");
        assertThat(InterviewPrompts.assess("Nurse", "junior", topic, prior, null).system()).doesNotContain("%s");
    }

    @Test
    void probe_carriesTheMissingPiece_andForbidsChangingTopic() {
        AnswerAnalysis gap = new AnswerAnalysis("rebuilt the pipeline", "result", "They never said the outcome.", null, true, false);

        InterviewPrompts.Prompt p = turn(new TurnPlan(TurnType.PROBE, topic), gap, "Priya");

        assertThat(p.system()).contains("Follow up on the candidate's LAST answer").contains("They never said the outcome.")
                .contains("do not change topic");
        assertThat(p.user()).contains("rebuilt the pipeline").contains("Gap: result");
    }

    @Test
    void newTopic_usesTheResumeHook_andTheTopicFocus() {
        InterviewPrompts.Prompt p = turn(new TurnPlan(TurnType.NEW_TOPIC, topic), AnswerAnalysis.NONE, "Priya");

        assertThat(p.system()).contains("Owning an outage").contains("A real incident they led").contains("the payments migration at Acme");
    }

    @Test
    void challenge_quotesTheClaim() {
        AnswerAnalysis claim = new AnswerAnalysis(null, "none", null, "Cut cost by 80 percent", true, false);

        assertThat(turn(new TurnPlan(TurnType.CHALLENGE, topic), claim, "Priya").system())
                .contains("Cut cost by 80 percent").contains("Stress-test");
    }

    @Test
    void opening_andWrapUp_cannotTerminate_butRegularTurnsMay() {
        assertThat(turn(TurnDirectorHelper.opening(), null, null).system()).contains("terminate must be false");
        assertThat(turn(TurnDirectorHelper.wrapUp(), AnswerAnalysis.NONE, "Priya").system()).contains("terminate must be false");
        assertThat(turn(new TurnPlan(TurnType.NEW_TOPIC, topic), AnswerAnalysis.NONE, "Priya").system())
                .contains("CLEAR, REPEATED pattern");
    }

    @Test
    void speakerKnowsWhoSpokeLast_andIsToldNotToReIntroduce() {
        String handoff = turn(new TurnPlan(TurnType.NEW_TOPIC, topic), AnswerAnalysis.NONE, "Priya").system();
        String sameSpeaker = turn(new TurnPlan(TurnType.NEW_TOPIC, topic), AnswerAnalysis.NONE, "Sam").system();

        assertThat(handoff).contains("previous question was asked by Priya").contains("don't re-introduce yourself");
        assertThat(sameSpeaker).contains("You asked the previous question yourself");
    }

    @Test
    void ruleBookBansStockPraise_andLimitsQuestionLength() {
        String system = turn(new TurnPlan(TurnType.NEW_TOPIC, topic), AnswerAnalysis.NONE, "Priya").system();

        assertThat(system).contains("Great answer").contains("Thanks for sharing").contains("at most 30 words")
                .contains("exactly ONE question").contains("voice transcripts");
    }

    @Test
    void transcript_labelsFollowUps_andMarksTheCurrentSpeaker() {
        List<TranscriptTurn> withProbe = List.of(
                new TranscriptTurn("Sam", "Tell me about an outage.", "We fixed it.", "new_topic"),
                new TranscriptTurn("Priya", "What did you do?", "I led the fix.", "probe"));

        InterviewPrompts.Prompt p = InterviewPrompts.turn("Engineer", null, sam, panel, withProbe, null,
                new TurnPlan(TurnType.NEW_TOPIC, topic), AnswerAnalysis.NONE, "Priya");

        assertThat(p.user()).contains("[Priya, follow-up]").contains("[Sam]");
        assertThat(p.system()).contains("Sam (Direct, terse) <- you");
    }

    @Test
    void assessor_isCalibratedToTheCandidatesLevel() {
        assertThat(InterviewPrompts.assess("Dev", "junior", topic, prior, null).system()).contains("Junior candidate");
        assertThat(InterviewPrompts.assess("Dev", "staff", topic, prior, null).system()).contains("Senior candidate");
        assertThat(InterviewPrompts.assess("Dev", null, topic, prior, null).system()).contains("Mid-level candidate");
    }

    @Test
    void noResume_meansTheModelIsToldNotToAssumeSpecifics() {
        InterviewPrompts.Prompt p = InterviewPrompts.turn("Dev", null, sam, panel, List.of(), null,
                TurnDirectorHelper.opening(), null, null);

        assertThat(p.user()).contains("No resume or portfolio on file").contains("Nothing yet - you are starting");
    }

    /** TurnDirector is package-private in another package, so build the two fixed plans directly. */
    private static final class TurnDirectorHelper {
        static TurnPlan opening() {
            return new TurnPlan(TurnType.OPENING, InterviewPlan.INTRO);
        }

        static TurnPlan wrapUp() {
            return new TurnPlan(TurnType.WRAP_UP, InterviewPlan.WRAP);
        }
    }
}
