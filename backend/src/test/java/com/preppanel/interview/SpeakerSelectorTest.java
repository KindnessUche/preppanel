package com.preppanel.interview;

import com.preppanel.llm.PanelistRosterEntry;
import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.InterviewPlan;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;
import com.preppanel.panel.PanelistArchetype;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakerSelectorTest {

    private static PanelistRosterEntry p(String archetypeId) {
        PanelistArchetype a = PanelistArchetype.byId(archetypeId);
        return new PanelistRosterEntry(a.displayName(), a);
    }

    private final PanelistRosterEntry hiringManager = p("hiring-manager");
    private final PanelistRosterEntry techLead = p("tech-lead");
    private final PanelistRosterEntry skeptic = p("skeptic");
    private final PanelistRosterEntry barRaiser = p("bar-raiser");
    private final PanelistRosterEntry peer = p("peer");

    private static TurnPlan plan(TurnType type, String lens) {
        return new TurnPlan(type, new InterviewPlan.Topic("t1", "Some topic", lens, "focus", null));
    }

    private static AnswerAnalysis gap(String gap) {
        return new AnswerAnalysis(null, gap, "detail", null, true, false);
    }

    @Test
    void theHiringManagerOpens_whoeverIsListedFirst() {
        var roster = List.of(techLead, skeptic, hiringManager);

        assertThat(SpeakerSelector.select(roster, TurnDirector.opening(), AnswerAnalysis.NONE, List.of())).isSameAs(hiringManager);
    }

    @Test
    void withoutAHiringManager_aPeerOpens() {
        var roster = List.of(techLead, peer, skeptic);

        assertThat(SpeakerSelector.select(roster, TurnDirector.opening(), AnswerAnalysis.NONE, List.of())).isSameAs(peer);
    }

    @Test
    void theSkepticTakesChallenges() {
        var roster = List.of(hiringManager, techLead, skeptic);

        assertThat(SpeakerSelector.select(roster, plan(TurnType.CHALLENGE, "ownership"), AnswerAnalysis.NONE,
                List.of(hiringManager.name(), techLead.name()))).isSameAs(skeptic);
    }

    @Test
    void theTechLeadTakesTechnicalGaps() {
        var roster = List.of(hiringManager, techLead, skeptic);

        assertThat(SpeakerSelector.select(roster, plan(TurnType.PROBE, "ownership"), gap("technical_depth"),
                List.of(hiringManager.name(), skeptic.name()))).isSameAs(techLead);
    }

    @Test
    void thePeerTakesCollaborationTopics() {
        var roster = List.of(hiringManager, techLead, peer);

        assertThat(SpeakerSelector.select(roster, plan(TurnType.NEW_TOPIC, "collaboration"), AnswerAnalysis.NONE,
                List.of(hiringManager.name(), techLead.name()))).isSameAs(peer);
    }

    @Test
    void theBarRaiserClosesTheInterview() {
        var roster = List.of(hiringManager, barRaiser, peer);

        assertThat(SpeakerSelector.select(roster, TurnDirector.wrapUp(), AnswerAnalysis.NONE,
                List.of(hiringManager.name(), peer.name(), hiringManager.name()))).isSameAs(barRaiser);
    }

    @Test
    void theWrapUpIsNotGivenToWhoeverJustSpoke_evenIfTheyAreTheNaturalCloser() {
        // Regression: the hiring manager asked the last topic question and then also closed the interview.
        var roster = List.of(hiringManager, techLead, skeptic, peer);
        var spoken = List.of(hiringManager.name(), peer.name(), skeptic.name(), techLead.name(), hiringManager.name());

        assertThat(SpeakerSelector.select(roster, TurnDirector.wrapUp(), AnswerAnalysis.NONE, spoken)).isNotSameAs(hiringManager);
    }

    @Test
    void aSinglePanelistAlwaysSpeaks() {
        assertThat(SpeakerSelector.select(List.of(skeptic), plan(TurnType.NEW_TOPIC, "technical"), AnswerAnalysis.NONE,
                List.of(skeptic.name(), skeptic.name()))).isSameAs(skeptic);
    }

    @Test
    void theSamePersonDoesNotSpeakTwiceInARow_whenSomeoneElseIsAvailable() {
        // Technical topic: the tech lead is the natural fit, but they just spoke.
        var roster = List.of(techLead, hiringManager);

        assertThat(SpeakerSelector.select(roster, plan(TurnType.NEW_TOPIC, "technical"), AnswerAnalysis.NONE,
                List.of(techLead.name()))).isSameAs(hiringManager);
    }

    @Test
    void everyoneGetsATurn_acrossAFullInterview() {
        var roster = List.of(hiringManager, techLead, skeptic, peer);
        var lenses = List.of("motivation", "ownership", "collaboration", "technical", "growth", "judgment");
        var spoken = new java.util.ArrayList<String>();

        TurnPlan opening = TurnDirector.opening();
        spoken.add(SpeakerSelector.select(roster, opening, AnswerAnalysis.NONE, spoken).name());
        for (String lens : lenses) {
            spoken.add(SpeakerSelector.select(roster, plan(TurnType.NEW_TOPIC, lens), AnswerAnalysis.NONE, spoken).name());
        }

        assertThat(spoken).contains(hiringManager.name(), techLead.name(), skeptic.name(), peer.name());
        for (int i = 1; i < spoken.size(); i++) {
            assertThat(spoken.get(i)).isNotEqualTo(spoken.get(i - 1));
        }
    }
}
