-- View count for listings.
-- Apply manually against existing databases once.
-- New imports should use backend/polygo.sql which already contains these changes.

ALTER TABLE listings
    ADD COLUMN views INT UNSIGNED NOT NULL DEFAULT 0 AFTER archived_at;
