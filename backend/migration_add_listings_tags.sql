-- Adds a tags column to listings so the "Product Tags" field in the Add/Edit
-- Product form is actually persisted instead of silently discarded.
-- Run on the MySQL instance (port 3306) after pulling this file.
ALTER TABLE listings
    ADD COLUMN tags VARCHAR(255) NOT NULL DEFAULT '' AFTER image_url;