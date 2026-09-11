-- Migration: Campus Pulse (campus_alerts) + Matrix Card verification storage
-- Run once against an existing `polygo` database, e.g.:
--   mysql -u root polygo < migration_add_campus_pulse.sql

USE polygo;

-- 1) Store the Matrix Card upload URL + verification state on the user row.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS verification_photo VARCHAR(500) NULL DEFAULT NULL AFTER is_private,
    ADD COLUMN IF NOT EXISTS verification_status ENUM('unverified','pending','approved','rejected') NOT NULL DEFAULT 'unverified' AFTER verification_photo;

-- 2) Campus Pulse feed.
CREATE TABLE IF NOT EXISTS campus_alerts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    user_name VARCHAR(120) NOT NULL,
    tag ENUM('ANNOUNCEMENT','REQUEST','FLASH SALE','EVENT') NOT NULL DEFAULT 'REQUEST',
    title VARCHAR(150) NOT NULL,
    body TEXT NOT NULL,
    status ENUM('pending','approved','hidden') NOT NULL DEFAULT 'approved',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_status_created (status, created_at),
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);