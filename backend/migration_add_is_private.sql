-- Migration: add account privacy flag (run once against the existing polygo database)
ALTER TABLE polygo.users
    ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE AFTER is_verified;