package com.preppanel.interview;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Answer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "question_id", nullable = false, unique = true)
    private UUID questionId;

    @Column(name = "answer_text", nullable = false, columnDefinition = "text")
    private String answerText;

    // JSONB column - stored as raw JSON string for v1, audio metrics come from the voice path (null for typed answers).
    // @ColumnTransformer casts the bind parameter explicitly, since Postgres won't
    // implicitly cast a plain varchar/null parameter into jsonb.
    @Column(name = "audio_metrics", columnDefinition = "jsonb")
    @org.hibernate.annotations.ColumnTransformer(write = "?::jsonb")
    private String audioMetrics;

    // The hidden assessor's read on this answer (AnswerAnalysis as JSON). Null for typed-legacy/failed assessments.
    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.ColumnTransformer(write = "?::jsonb")
    private String analysis;

    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;

    @PrePersist
    void onCreate() {
        this.submittedAt = Instant.now();
    }
}
