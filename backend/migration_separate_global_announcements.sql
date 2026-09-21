-- Separate official global announcements from student Campus Pulse threads.
ALTER TABLE campus_alerts
    ADD COLUMN IF NOT EXISTS is_global TINYINT(1) NOT NULL DEFAULT 0 AFTER status;

UPDATE campus_alerts
SET is_global = 1
WHERE user_name = 'PolyGo+ Admin';
