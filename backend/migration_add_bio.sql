-- Migration: add user bio column (run once against the existing polygo database)
ALTER TABLE polygo.users
    ADD COLUMN bio VARCHAR(255) NULL DEFAULT NULL AFTER mobile;