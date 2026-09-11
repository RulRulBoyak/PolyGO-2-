-- PolyGo+ hardening migration (2026-09)
-- Apply from the repo root with:
--   cmd /c "C:\xampp\mysql\bin\mysql -u root -P 3306 polygo < backend\migration_hardening.sql"
-- Safe to run repeatedly (idempotent via INFORMATION_SCHEMA guards).

USE polygo;

-- 1) Rate limiter backing table
CREATE TABLE IF NOT EXISTS rate_limits (
    bucket VARBINARY(255) NOT NULL PRIMARY KEY,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    window_start INT UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) OTP verification codes (one active code per email, hashed, expiring)
CREATE TABLE IF NOT EXISTS verification_codes (
    email VARCHAR(160) NOT NULL PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) Idempotency key for green-impact credits (prevents double-counting)
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE impact_entries ADD UNIQUE KEY uq_user_tx (user_id, transaction_id)',
    'SELECT 1')
FROM information_schema.statistics
WHERE table_schema = 'polygo' AND table_name = 'impact_entries' AND index_name = 'uq_user_tx');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) Safe "add column if missing" helpers for columns added by the app/backend fixes
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE users ADD COLUMN profile_pic_url VARCHAR(500)',
    'SELECT 1')
FROM information_schema.columns
WHERE table_schema = 'polygo' AND table_name = 'users' AND column_name = 'profile_pic_url');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE users ADD COLUMN fcm_token VARCHAR(500)',
    'SELECT 1')
FROM information_schema.columns
WHERE table_schema = 'polygo' AND table_name = 'users' AND column_name = 'fcm_token');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    "ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'Student'",
    'SELECT 1')
FROM information_schema.columns
WHERE table_schema = 'polygo' AND table_name = 'users' AND column_name = 'role');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE listings ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
    'SELECT 1')
FROM information_schema.columns
WHERE table_schema = 'polygo' AND table_name = 'listings' AND column_name = 'updated_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reviews ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP',
    'SELECT 1')
FROM information_schema.columns
WHERE table_schema = 'polygo' AND table_name = 'reviews' AND column_name = 'created_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE transactions ADD COLUMN impact_credited BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1')
FROM information_schema.columns
WHERE table_schema = 'polygo' AND table_name = 'transactions' AND column_name = 'impact_credited');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;