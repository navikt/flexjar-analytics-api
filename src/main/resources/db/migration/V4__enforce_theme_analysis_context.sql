-- Enforce explicit analysis context on themes (no implicit defaults)

-- Drop the default added in V3 so callers must supply analysis_context explicitly.
ALTER TABLE text_theme
ALTER COLUMN analysis_context DROP DEFAULT;

-- Enforce allowed values (matches AnalysisContext enum)
ALTER TABLE text_theme
ADD CONSTRAINT check_text_theme_analysis_context
CHECK (analysis_context IN ('GENERAL_FEEDBACK', 'BLOCKER'));
