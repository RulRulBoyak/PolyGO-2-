-- Event card theming: apply once to existing PolyGo databases.
ALTER TABLE campus_events
    ADD COLUMN theme VARCHAR(40) NOT NULL DEFAULT 'default' AFTER is_published,
    ADD COLUMN accent_color CHAR(7) NULL DEFAULT NULL AFTER theme,
    ADD COLUMN emoji VARCHAR(16) NULL DEFAULT NULL AFTER accent_color,
    ADD COLUMN label VARCHAR(40) NULL DEFAULT NULL AFTER emoji,
    ADD COLUMN cover_url VARCHAR(500) NULL DEFAULT NULL AFTER label,
    ADD COLUMN is_featured TINYINT(1) NOT NULL DEFAULT 0 AFTER cover_url;

ALTER TABLE campus_events
    ADD INDEX idx_campus_events_featured_starts (is_featured, starts_at);