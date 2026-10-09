package com.preppanel.profile;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "resume_text", columnDefinition = "text")
    private String resumeText;

    @Column(name = "resume_filename")
    private String resumeFilename;

    @Column(name = "github_username")
    private String githubUsername;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "portfolio_urls", columnDefinition = "text[]")
    @Builder.Default
    private List<String> portfolioUrls = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "parsed_skills", columnDefinition = "text[]")
    @Builder.Default
    private List<String> parsedSkills = List.of();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.portfolioUrls == null) this.portfolioUrls = List.of();
        if (this.parsedSkills == null) this.parsedSkills = List.of();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
