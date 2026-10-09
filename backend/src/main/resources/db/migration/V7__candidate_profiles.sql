-- Resume/portfolio ingestion - previously entirely absent (PRD v3 gap #7).
-- One profile per user; resume re-upload overwrites resume_text/parsedSkills
-- in place rather than versioning, which is fine for v1.
CREATE TABLE candidate_profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    resume_text       TEXT,
    resume_filename   VARCHAR(255),
    github_username   VARCHAR(100),
    portfolio_urls    TEXT[] NOT NULL DEFAULT '{}',
    parsed_skills     TEXT[] NOT NULL DEFAULT '{}',
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_candidate_profiles_user_id ON candidate_profiles(user_id);
