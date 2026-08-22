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
@Table(name = "scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Score {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "answer_id", nullable = false, unique = true)
    private UUID answerId;

    @Column(name = "content_score", nullable = false)
    private int contentScore;

    @Column(name = "structure_score", nullable = false)
    private int structureScore;

    @Column(name = "technical_score")
    private Integer technicalScore;

    @Column(name = "delivery_score")
    private Integer deliveryScore;

    @Column(name = "feedback_text", nullable = false, columnDefinition = "text")
    private String feedbackText;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
