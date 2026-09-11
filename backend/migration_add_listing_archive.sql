-- 90-day auto-archive for stale listings.
-- Apply manually against existing databases (e.g. XAMPP phpMyAdmin -> polygo -> SQL) once.
-- New imports should use backend/polygo.sql which already contains these changes.

ALTER TABLE listings
    ADD COLUMN archived_at TIMESTAMP NULL DEFAULT NULL AFTER updated_at;

CREATE INDEX idx_listings_archived ON listings (archived_at, owner_id);

-- Backfill: archive listings older than 90 days that are still marked available.
UPDATE listings
    SET archived_at = NOW()
    WHERE archived_at IS NULL
      AND is_available = 1
      AND created_at < NOW() - INTERVAL 90 DAY;