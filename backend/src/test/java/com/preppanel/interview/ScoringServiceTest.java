package com.preppanel.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preppanel.exception.ApiException;
import com.preppanel.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionQuestionRepository questionRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private LlmService llmService;

    private ScoringService scoringService;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService(
                sessionRepository, questionRepository, answerRepository, scoreRepository,
                llmService, new ObjectMapper(), new com.preppanel.transcription.DeliveryScorer());
    }

    private InterviewSession sessionOwnedBy(UUID owner) {
        return InterviewSession.builder()
                .id(sessionId).userId(owner).role("Backend Engineer").tone("neutral")
                .status("in_progress").panelists("[]")
                .build();
    }

    private SessionQuestion question(int seq) {
        return SessionQuestion.builder()
                .id(UUID.randomUUID()).sessionId(sessionId).sequenceNum(seq)
                .questionText("Tell me about a conflict you resolved.")
                .questionType("behavioral").source("bank")
                .build();
    }

    private Answer answerFor(UUID questionId) {
        return Answer.builder().id(UUID.randomUUID()).questionId(questionId)
                .answerText("I handled it calmly.").build();
    }

    @Test
    void scoreSession_crossUserAccess_throws404NotFound_notForbidden() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionOwnedBy(otherUserId)));

        assertThatThrownBy(() -> scoringService.scoreSession(userId, sessionId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));

        verifyNoInteractions(llmService);
    }

    @Test
    void scoreSession_skipsAlreadyScoredAnswers_idempotently() {
        InterviewSession session = sessionOwnedBy(userId);
        SessionQuestion q1 = question(1);
        Answer a1 = answerFor(q1.getId());

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId))
                .thenReturn(List.of(q1));
        when(answerRepository.findByQuestionId(q1.getId())).thenReturn(Optional.of(a1));
        when(scoreRepository.findByAnswerId(a1.getId())).thenReturn(Optional.of(mock(Score.class)));

        scoringService.scoreSession(userId, sessionId);

        // Already scored - the LLM must never be called again for this answer.
        verifyNoInteractions(llmService);
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void scoreSession_skipsUnansweredQuestions() {
        InterviewSession session = sessionOwnedBy(userId);
        SessionQuestion q1 = question(1);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId))
                .thenReturn(List.of(q1));
        when(answerRepository.findByQuestionId(q1.getId())).thenReturn(Optional.empty());

        scoringService.scoreSession(userId, sessionId);

        verifyNoInteractions(llmService);
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void scoreSession_malformedLlmOutput_fallsBackToNeutralScoreAfterOneRetry() {
        InterviewSession session = sessionOwnedBy(userId);
        SessionQuestion q1 = question(1);
        Answer a1 = answerFor(q1.getId());

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId))
                .thenReturn(List.of(q1));
        when(answerRepository.findByQuestionId(q1.getId())).thenReturn(Optional.of(a1));
        when(scoreRepository.findByAnswerId(a1.getId())).thenReturn(Optional.empty());
        // LLM returns garbage both attempts - not valid JSON at all.
        when(llmService.scoreAnswer(anyString(), anyString(), anyString())).thenReturn("not json");

        scoringService.scoreSession(userId, sessionId);

        verify(llmService, times(2)).scoreAnswer(anyString(), anyString(), anyString());

        var captor = org.mockito.ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository).save(captor.capture());
        Score saved = captor.getValue();
        assertThat(saved.getContentScore()).isEqualTo(50);
        assertThat(saved.getStructureScore()).isEqualTo(50);
        assertThat(saved.getFeedbackText()).contains("unavailable");
    }

    @Test
    void scoreSession_validLlmOutput_stripsMarkdownFences_andClampsOutOfRangeScores() {
        InterviewSession session = sessionOwnedBy(userId);
        SessionQuestion q1 = question(1);
        Answer a1 = answerFor(q1.getId());

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId))
                .thenReturn(List.of(q1));
        when(answerRepository.findByQuestionId(q1.getId())).thenReturn(Optional.of(a1));
        when(scoreRepository.findByAnswerId(a1.getId())).thenReturn(Optional.empty());
        when(llmService.scoreAnswer(anyString(), anyString(), anyString())).thenReturn(
                "```json\n{\"contentScore\": 130, \"structureScore\": -20, \"feedbackText\": \"Good STAR structure.\"}\n```"
        );

        scoringService.scoreSession(userId, sessionId);

        var captor = org.mockito.ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository).save(captor.capture());
        Score saved = captor.getValue();
        assertThat(saved.getContentScore()).isEqualTo(100); // clamped from 130
        assertThat(saved.getStructureScore()).isEqualTo(0);  // clamped from -20
        assertThat(saved.getFeedbackText()).isEqualTo("Good STAR structure.");
    }

    private SessionQuestion turn(int seq, String turnType) {
        SessionQuestion q = question(seq);
        q.setTurnType(turnType);
        return q;
    }

    @Test
    void scoreSession_doesNotScoreTheOpeningOrTheWrapUp() {
        InterviewSession session = sessionOwnedBy(userId);
        SessionQuestion opening = turn(1, "opening");
        SessionQuestion wrapUp = turn(2, "wrap_up");
        Answer a1 = answerFor(opening.getId());
        Answer a2 = answerFor(wrapUp.getId());

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of(opening, wrapUp));
        when(answerRepository.findByQuestionId(opening.getId())).thenReturn(Optional.of(a1));
        when(answerRepository.findByQuestionId(wrapUp.getId())).thenReturn(Optional.of(a2));

        scoringService.scoreSession(userId, sessionId);

        verifyNoInteractions(llmService);
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void scoreSession_tagsFollowUps_soTheJudgeDoesNotExpectAFullStarStory() {
        InterviewSession session = sessionOwnedBy(userId);
        SessionQuestion probe = turn(3, "probe");
        Answer a = answerFor(probe.getId());

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of(probe));
        when(answerRepository.findByQuestionId(probe.getId())).thenReturn(Optional.of(a));
        when(scoreRepository.findByAnswerId(a.getId())).thenReturn(Optional.empty());
        when(llmService.scoreAnswer(anyString(), startsWith("[Follow-up] "), anyString())).thenReturn(
                "{\"contentScore\":70,\"structureScore\":65,\"technicalScore\":null,\"feedbackText\":\"Direct and specific.\"}");

        scoringService.scoreSession(userId, sessionId);

        verify(llmService).scoreAnswer(anyString(), startsWith("[Follow-up] "), anyString());
        verify(scoreRepository).save(any(Score.class));
    }
}
