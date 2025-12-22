-- Initial schema - matches existing flexjar-backend
-- This allows us to use the same database

CREATE TABLE IF NOT EXISTS feedback
(
    id            VARCHAR DEFAULT gen_random_uuid() PRIMARY KEY,
    opprettet     TIMESTAMP WITH TIME ZONE NOT NULL,
    feedback_json TEXT NOT NULL,
    team          VARCHAR(255) NOT NULL,
    app           VARCHAR(255) NOT NULL,
    tags          TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_opprettet ON feedback(opprettet);
CREATE INDEX IF NOT EXISTS idx_feedback_team ON feedback(team);
CREATE INDEX IF NOT EXISTS idx_feedback_team_app ON feedback(team, app);
