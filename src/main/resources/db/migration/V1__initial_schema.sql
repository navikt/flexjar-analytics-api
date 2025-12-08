-- Initial schema - matches existing flexjar-backend
-- This allows us to use the same database

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS feedback
(
    id            VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    opprettet     TIMESTAMP WITH TIME ZONE,
    feedback_json TEXT NOT NULL,
    team          VARCHAR(255) DEFAULT 'flex',
    app           VARCHAR(255) NULL,
    tags          TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_opprettet ON feedback(opprettet);
CREATE INDEX IF NOT EXISTS idx_feedback_team ON feedback(team);
CREATE INDEX IF NOT EXISTS idx_feedback_team_app ON feedback(team, app);
