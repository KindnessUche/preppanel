CREATE TABLE scores (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_id           UUID NOT NULL UNIQUE REFERENCES answers(id) ON DELETE CASCADE,
    content_score       INT NOT NULL,           -- 0-100
    structure_score     INT NOT NULL,           -- 0-100, STAR-format adherence etc.
    technical_score     INT,                    -- 0-100, nullable (only for technical questions)
    delivery_score      INT,                    -- 0-100, nullable (reserved for voice mode metrics)
    feedback_text       TEXT NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);
