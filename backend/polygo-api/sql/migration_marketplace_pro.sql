-- Marketplace Pro: professional listing attributes, trust-based seller
-- identity (follows), price-watch alerts, and per-listing publishing toggles.
-- Apply ONCE against the live database:
--   Get-Content migration_marketplace_pro.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root polygo
-- Mirrors: backend/polygo.sql (fresh installs).

ALTER TABLE listings
    ADD COLUMN `condition` ENUM('New','Used - Like New','Used - Good','Used - Fair')
        NOT NULL DEFAULT 'New' AFTER price,
    ADD COLUMN original_price DECIMAL(10,2) NULL DEFAULT NULL AFTER `condition`,
    ADD COLUMN auto_reply TINYINT(1) NOT NULL DEFAULT 0 AFTER location,
    ADD COLUMN hide_from_friends TINYINT(1) NOT NULL DEFAULT 0 AFTER auto_reply;

-- "Follow" social proof: follower -> followed (self-follow prevented in code).
CREATE TABLE IF NOT EXISTS user_follows (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT UNSIGNED NOT NULL,
    followed_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_follow_pair (follower_id, followed_id),
    KEY idx_follow_followed (followed_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_follow_followed FOREIGN KEY (followed_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Price-drop / availability "Alert" watches per listing.
CREATE TABLE IF NOT EXISTS listing_alerts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    listing_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_alert_pair (user_id, listing_id),
    KEY idx_alert_listing (listing_id),
    CONSTRAINT fk_listing_alert_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_listing_alert_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
