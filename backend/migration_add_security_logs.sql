-- Migration: add emergency alert logging and the pickup deal phase
-- Run once against the existing polygo database.

CREATE TABLE IF NOT EXISTS polygo.security_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    thread_id BIGINT UNSIGNED NULL,
    listing_id BIGINT UNSIGNED NULL,
    landmark VARCHAR(120) DEFAULT NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_security_user FOREIGN KEY (user_id) REFERENCES polygo.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_security_thread FOREIGN KEY (thread_id) REFERENCES polygo.threads(id) ON DELETE SET NULL
);

ALTER TABLE polygo.transactions
    MODIFY status ENUM('offer_sent','accepted','pickup','completed','cancelled') NOT NULL DEFAULT 'offer_sent';