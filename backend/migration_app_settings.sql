-- Migration: maintenance kill-switch settings table (app_settings)
-- Mirrors CREATE TABLE at the end of the master schema (backend/polygo.sql).
-- Applied manually once against the live DB; the admin panel (admin/maintenance.php)
-- upserts 'maintenance' / 'maintenance_message' and ../status.php reads them.
-- Safe to re-run (idempotent).

CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL
);

INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES ('maintenance', '0');
INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES ('maintenance_message', '');
INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES ('home_messages', '');
INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES ('home_message_interval_seconds', '8');