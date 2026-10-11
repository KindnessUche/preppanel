package com.preppanel.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preppanel.interview.dto.SessionResponse;
import com.preppanel.interview.dto.SubmitAnswerRequest;
import com.preppanel.llm.LlmService;
import com.preppanel.llm.PanelistRosterEntry;
import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;
import com.preppanel.profile.ProfileService;
import com.preppanel.question.QuestionBank;
import com.preppanel.question.QuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Walks the planned-interview loop with the model mocked: assess the answer, let the director pick the
 * move, let the selector pick the speaker, have them speak. These are the behaviours that make the
 * interview feel like a conversation instead of a form.
 */
// Lenient: the shared mid-interview wiring is deliberately more than any single test (e.g. wrap-up) touches.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewFlowTest {

    private static final String PANELISTS_JSON =
            "[{\"archetypeId\":\"hiring-manager\",\"name\":null},"
                    + "{\"archetypeId\":\"tech-lead\",\"name\":null},"
                    + "{\"archetypeId\":\"skeptic\",\"name\":null}]";

    private static final String SPEAKER_JSON =
            "{\"reaction\":\"Rebuilt the whole pipeline - that's a big call.\",\"moodShift\":\"neutral\","
                    + "\"nextQuestion\":\"Which part of it was slow?\",\"terminate\":false}";

    private static final String LONG_ANSWER = "We rebuilt the whole data pipeline and it ran much faster afterwards";

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionQuestionRepository questionRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ProfileService profileService;

    private InterviewService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    private SessionQuestion opening;
    private SessionQuestion current;
    private InterviewSession session;
    private Answer savedAnswer;

    @BeforeEach
    void setUp() {
        service = new InterviewService(sessionRepository, questionRepository, answerRepository,
                questionBankRepository, llmService, profileService, new ObjectMapper());
    }

    private SessionQuestion question(int seq, String turnType, String topicId, String panelist) {
        return SessionQuestion.builder()
                .id(UUID.randomUUID()).sessionId(sessionId).sequenceNum(seq)
                .questionText("Question " + seq).questionType("behavioral").source("llm_generated")
                .panelistName(panelist).turnType(turnType).topicId(topicId).build();
    }

    /** Candidate is answering question 2 (topic t1, asked by the skeptic) after an opening from the hiring manager. */
    private void midInterview(String currentTurnType) {
        opening = question(1, "opening", "intro", "The Hiring Manager");
        current = question(2, currentTurnType, "t1", "The Skeptic");
        session = InterviewSession.builder()
                .id(sessionId).userId(userId).role("Backend Engineer").tone("neutral").mode("text")
                .status("in_progress").panelists(PANELISTS_JSON).build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.countBySessionId(sessionId)).thenReturn(2);
        when(questionRepository.findBySessionIdAndSequenceNum(sessionId, 2)).thenReturn(Optional.of(current));
        when(answerRepository.save(any(Answer.class))).thenAnswer(inv -> {
            savedAnswer = inv.getArgument(0);
            return savedAnswer;
        });
        // Not answered yet on the first look; afterwards the DB would return what we just saved.
        when(answerRepository.findByQuestionId(current.getId()))
                .thenReturn(Optional.empty())
                .thenAnswer(inv -> Optional.ofNullable(savedAnswer));
        when(answerRepository.findByQuestionId(opening.getId()))
                .thenReturn(Optional.of(Answer.builder().id(UUID.randomUUID()).questionId(opening.getId())
                        .answerText("I'm a backend engineer with six years of experience").build()));
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of(opening, current));
        when(questionRepository.save(any(SessionQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void technicallyVagueAnswer_becomesAProbe_askedByTheTechLead() {
        midInterview("new_topic");
        when(llmService.assessAnswer(anyString(), any(), any(), anyList(), any())).thenReturn(
                "{\"standout\":\"rebuilt the whole data pipeline\",\"gap\":\"technical_depth\","
                        + "\"gapDetail\":\"They never said how the rebuild made it faster.\","
                        + "\"claimToChallenge\":null,\"answeredQuestion\":true,\"rambling\":false}");
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn(SPEAKER_JSON);

        SessionResponse response = service.submitAnswer(userId, sessionId, new SubmitAnswerRequest(LONG_ANSWER));

        assertThat(response.completed()).isFalse();
        assertThat(response.currentQuestion().turnType()).isEqualTo("probe");
        assertThat(response.currentQuestion().panelistArchetypeId()).isEqualTo("tech-lead");

        // The probe stays on the same topic, and the analysis was stored on the answer.
        ArgumentCaptor<SessionQuestion> saved = ArgumentCaptor.forClass(SessionQuestion.class);
        verify(questionRepository).save(saved.capture());
        assertThat(saved.getValue().getTopicId()).isEqualTo("t1");
        assertThat(savedAnswer.getAnalysis()).contains("technical_depth");

        // The speaker was told it is a probe, what was missing, and who spoke before them.
        ArgumentCaptor<TurnPlan> plan = ArgumentCaptor.forClass(TurnPlan.class);
        ArgumentCaptor<AnswerAnalysis> analysis = ArgumentCaptor.forClass(AnswerAnalysis.class);
        ArgumentCaptor<String> previous = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PanelistRosterEntry> speaker = ArgumentCaptor.forClass(PanelistRosterEntry.class);
        verify(llmService).generatePanelTurn(anyString(), any(), speaker.capture(), anyList(), anyList(), any(),
                plan.capture(), analysis.capture(), previous.capture());
        assertThat(plan.getValue().type()).isEqualTo(TurnType.PROBE);
        assertThat(analysis.getValue().gapDetail()).contains("how the rebuild made it faster");
        assertThat(previous.getValue()).isEqualTo("The Skeptic");
        assertThat(speaker.getValue().archetype().id()).isEqualTo("tech-lead");
    }

    @Test
    void solidAnswer_movesToTheNextTopic() {
        midInterview("new_topic");
        when(llmService.assessAnswer(anyString(), any(), any(), anyList(), any())).thenReturn(
                "{\"standout\":\"cut p99 from 900ms to 200ms by batching writes\",\"gap\":\"none\","
                        + "\"gapDetail\":null,\"claimToChallenge\":null,\"answeredQuestion\":true,\"rambling\":false}");
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn(SPEAKER_JSON);

        SessionResponse response = service.submitAnswer(userId, sessionId, new SubmitAnswerRequest(LONG_ANSWER));

        assertThat(response.currentQuestion().turnType()).isEqualTo("new_topic");
        ArgumentCaptor<SessionQuestion> saved = ArgumentCaptor.forClass(SessionQuestion.class);
        verify(questionRepository).save(saved.capture());
        assertThat(saved.getValue().getTopicId()).isEqualTo("t2"); // t1 is covered, so the next topic
    }

    @Test
    void veryShortAnswer_skipsTheAssessor_andIsProbed() {
        midInterview("new_topic");
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn(SPEAKER_JSON);

        SessionResponse response = service.submitAnswer(userId, sessionId, new SubmitAnswerRequest("Not really."));

        verify(llmService, never()).assessAnswer(anyString(), any(), any(), anyList(), any());
        assertThat(response.currentQuestion().turnType()).isEqualTo("probe");
        assertThat(savedAnswer.getAnalysis()).contains("too short");
    }

    @Test
    void assessorFailure_neverBlocksTheInterview_itJustMovesOn() {
        midInterview("new_topic");
        when(llmService.assessAnswer(anyString(), any(), any(), anyList(), any()))
                .thenThrow(new RuntimeException("timeout"));
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn(SPEAKER_JSON);

        SessionResponse response = service.submitAnswer(userId, sessionId, new SubmitAnswerRequest(LONG_ANSWER));

        assertThat(response.currentQuestion().turnType()).isEqualTo("new_topic");
    }

    @Test
    void answeringTheWrapUp_completesTheSession_withoutAnyModelCall() {
        midInterview("wrap_up");

        SessionResponse response = service.submitAnswer(userId, sessionId,
                new SubmitAnswerRequest("No, that covers it. Thank you all."));

        assertThat(response.completed()).isTrue();
        assertThat(response.terminatedByPanel()).isFalse();
        verifyNoInteractions(llmService);
    }

    @Test
    void panelCanEndTheInterview_onALaterTurn() {
        midInterview("new_topic");
        when(llmService.assessAnswer(anyString(), any(), any(), anyList(), any())).thenReturn(
                "{\"gap\":\"none\",\"answeredQuestion\":true,\"rambling\":false}");
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn("{\"reaction\":\"\",\"moodShift\":\"negative\",\"nextQuestion\":\"\","
                        + "\"terminate\":true,\"terminateReason\":\"Sustained hostility.\"}");

        SessionResponse response = service.submitAnswer(userId, sessionId, new SubmitAnswerRequest(LONG_ANSWER));

        assertThat(response.completed()).isTrue();
        assertThat(response.terminatedByPanel()).isTrue();
        assertThat(response.terminationReason()).isEqualTo("Sustained hostility.");
    }

    @Test
    void speakerFailureOnALaterTurn_fallsBackToTheQuestionBank_withoutCountingAsATopic() {
        midInterview("new_topic");
        when(llmService.assessAnswer(anyString(), any(), any(), anyList(), any())).thenReturn(
                "{\"gap\":\"none\",\"answeredQuestion\":true,\"rambling\":false}");
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("rate limited"));
        when(questionBankRepository.findRandomUnusedForSession(eq(sessionId), anyString()))
                .thenReturn(QuestionBank.builder().id(UUID.randomUUID())
                        .questionText("Tell me about a time you disagreed with a teammate.")
                        .questionType("behavioral").active(true).build());

        SessionResponse response = service.submitAnswer(userId, sessionId, new SubmitAnswerRequest(LONG_ANSWER));

        assertThat(response.currentQuestion().questionText()).contains("disagreed with a teammate");
        assertThat(response.currentQuestion().turnType()).isEqualTo("new_topic");
        ArgumentCaptor<SessionQuestion> saved = ArgumentCaptor.forClass(SessionQuestion.class);
        verify(questionRepository).save(saved.capture());
        assertThat(saved.getValue().getTopicId()).startsWith("bank-"); // doesn't tick off a plan topic
    }
}
