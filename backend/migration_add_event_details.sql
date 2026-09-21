-- Event detail extras: apply once to existing PolyGo databases.
-- Extends the campus_events theming migration (adds detail-page configuration).
ALTER TABLE campus_events
    ADD COLUMN organizer_name VARCHAR(120) NULL DEFAULT NULL AFTER is_featured,
    ADD COLUMN organizer_contact VARCHAR(120) NULL DEFAULT NULL AFTER organizer_name,
    ADD COLUMN registration_url VARCHAR(500) NULL DEFAULT NULL AFTER organizer_contact,
    ADD COLUMN map_url VARCHAR(300) NULL DEFAULT NULL AFTER registration_url,
    ADD COLUMN capacity INT UNSIGNED NULL DEFAULT NULL AFTER map_url;