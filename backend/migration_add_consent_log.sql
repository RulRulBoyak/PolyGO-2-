-- PDPA 2010 consent audit: record when a user agreed to the community rules.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS consent_agreed_at DATETIME NULL DEFAULT NULL AFTER password_hash;