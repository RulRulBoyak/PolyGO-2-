-- migration_review_uniqueness.sql
-- Allow at most one review per (reviewer_id, listing_id) pair.
-- APPLY ONCE against the live database (manual step), e.g.:
--   Get-Content migration_review_uniqueness.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root polygo

-- 1) Dedupe first: keep the earliest review per pair, drop the rest.
DELETE r1 FROM reviews r1
INNER JOIN reviews r2
  ON r1.reviewer_id = r2.reviewer_id
 AND r1.listing_id  = r2.listing_id
 AND r1.id > r2.id;

-- 2) Enforce uniqueness going forward.
ALTER TABLE reviews
  ADD UNIQUE KEY uq_reviewer_listing (reviewer_id, listing_id);