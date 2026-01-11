-- Add analysis context to text themes so they can be scoped per use-case (e.g. discovery vs blocker)

ALTER TABLE text_theme
ADD COLUMN analysis_context TEXT NOT NULL DEFAULT 'GENERAL_FEEDBACK';

CREATE INDEX IF NOT EXISTS idx_text_theme_team_context
ON text_theme(team, analysis_context);
