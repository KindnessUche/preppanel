-- Planned interviews: each session now carries a plan (the topics the panel intends to cover),
-- and every question records what kind of turn it was and which topic it belongs to.
-- The plan is small and read-mostly, so it's stored as JSON like the panelist roster.
ALTER TABLE interview_sessions ADD COLUMN plan JSONB;

-- opening | new_topic | probe | challenge | wrap_up. Existing rows are plain topic questions.
ALTER TABLE questions ADD COLUMN turn_type VARCHAR(20) NOT NULL DEFAULT 'new_topic';
ALTER TABLE questions ADD COLUMN topic_id VARCHAR(64);

-- The hidden assessor's read on each answer (what was missing, what stood out). Never shown to the candidate.
ALTER TABLE answers ADD COLUMN analysis JSONB;
