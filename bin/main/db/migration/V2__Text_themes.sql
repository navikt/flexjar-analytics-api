-- Text analysis themes for grouping free-text responses
-- Teams can define keywords that match to named themes
-- These themes can be used across ALL survey types (Rating, Discovery, TopTasks, etc.)

CREATE TABLE text_theme (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team TEXT NOT NULL,
    name TEXT NOT NULL,
    keywords TEXT[] NOT NULL,  -- {"utbetalt", "penger", "konto"}
    color TEXT,                -- Hex color for visualization
    priority INT DEFAULT 0,    -- Higher = matched first in CASE WHEN
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_text_theme_team ON text_theme(team);

-- Ensure unique theme names per team
CREATE UNIQUE INDEX idx_text_theme_team_name ON text_theme(team, name);
