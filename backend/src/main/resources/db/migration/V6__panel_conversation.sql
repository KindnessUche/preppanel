-- Multi-panelist support: a session now carries a roster of panelists
-- (archetype + optional custom name), stored as JSON since the roster is
-- small, read-mostly, and doesn't need its own relational table for v1.
ALTER TABLE interview_sessions ADD COLUMN panelists JSONB NOT NULL DEFAULT '[]';

-- The panel itself can end a session early (sustained hostility/refusal to
-- engage) - distinct from the candidate clicking "End early" themselves.
ALTER TABLE interview_sessions ADD COLUMN terminated_by_panel BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE interview_sessions ADD COLUMN termination_reason TEXT;

-- Calibrates question difficulty/depth - replaces the "paste a LinkedIn URL"
-- idea (unreliable/against ToS to scrape) with a simple self-reported level.
ALTER TABLE interview_sessions ADD COLUMN experience_level VARCHAR(20);

-- Which panelist asked this question, and their brief in-character reaction
-- to the candidate's previous answer (empty for the first question).
ALTER TABLE questions ADD COLUMN panelist_archetype_id VARCHAR(50);
ALTER TABLE questions ADD COLUMN panelist_name VARCHAR(100);
ALTER TABLE questions ADD COLUMN reaction_text TEXT;
ALTER TABLE questions ADD COLUMN mood_shift VARCHAR(20);
