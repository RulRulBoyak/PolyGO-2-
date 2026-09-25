-- Ban lifecycle: enforced server-side by config.php::verify_jwt()
-- (isUserBanned) and by the admin panel at admin/users.php.
--
-- NOTE: is_banned was already applied to the LIVE database by the admin panel
-- (Phase 2) — this file (re)declares it idempotently and adds banned_at.
-- Banned accounts: login.php/google_login.php reject them with HTTP 401;
-- NotificationManager::sendToUser() skips them; announcements exclude them.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_banned TINYINT(1) NOT NULL DEFAULT 0 AFTER is_verified,
    ADD COLUMN IF NOT EXISTS banned_at DATETIME NULL DEFAULT NULL AFTER is_banned;
