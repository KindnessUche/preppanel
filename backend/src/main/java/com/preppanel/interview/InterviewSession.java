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
@Table(name = "interview_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String role;

    @Column(nullable = false)
    private String mode; // text | voice

    @Column(nullable = false)
    private String status; // in_progress | completed

    @Column(nullable = false)
    private String tone; // friendly | neutral | direct

    // Roster of panelists for this session, stored as raw JSON (small, read-mostly -
    // doesn't warrant its own table for v1). Serialized/deserialized in InterviewService.
    @Column(nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.ColumnTransformer(write = "?::jsonb")
    private String panelists;

    @Column(name = "terminated_by_panel", nullable = false)
    private boolean terminatedByPanel;

    @Column(name = "termination_reason", columnDefinition = "text")
    private String terminationReason;

    @Column(name = "experience_level")
    private String experienceLevel; // junior | mid | senior | staff - optional

    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        this.startedAt = Instant.now();
        if (this.status == null) this.status = "in_progress";
        if (this.mode == null) this.mode = "text";
        if (this.tone == null) this.tone = "neutral";
        if (this.panelists == null) this.panelists = "[]";
    }
}
