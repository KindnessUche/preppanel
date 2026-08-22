CREATE TABLE interview_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(120),
    mode            VARCHAR(10) NOT NULL DEFAULT 'text',   -- text | voice
    status          VARCHAR(20) NOT NULL DEFAULT 'in_progress', -- in_progress | completed
    started_at      TIMESTAMP NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP
);

CREATE INDEX idx_sessions_user_id ON interview_sessions(user_id);

-- A question as it was actually asked within a session (copied/referenced from question_bank)
CREATE TABLE questions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_bank_id    UUID REFERENCES question_bank(id),
    sequence_num        INT NOT NULL,
    question_text       TEXT NOT NULL,
    question_type       VARCHAR(30) NOT NULL DEFAULT 'behavioral',
    source              VARCHAR(20) NOT NULL DEFAULT 'bank'  -- bank | llm_generated | company_research
);

CREATE UNIQUE INDEX idx_questions_session_sequence ON questions(session_id, sequence_num);

CREATE TABLE answers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID NOT NULL UNIQUE REFERENCES questions(id) ON DELETE CASCADE,
    answer_text     TEXT NOT NULL,
    audio_metrics   JSONB,
    submitted_at    TIMESTAMP NOT NULL DEFAULT now()
);
