-- Supports the "casting" tone preset from the UI/UX design doc's free tier:
-- one generic interviewer, three tone presets (friendly | neutral | direct).
-- Full archetype library is a later, paid-tier feature - not modeled yet.
ALTER TABLE interview_sessions ADD COLUMN tone VARCHAR(20) NOT NULL DEFAULT 'neutral';
