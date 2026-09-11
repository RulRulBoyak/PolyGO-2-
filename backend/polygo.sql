CREATE DATABASE IF NOT EXISTS polygo
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE polygo;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    student_id VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(160) NOT NULL UNIQUE,
    mobile VARCHAR(30),
    bio VARCHAR(255) NULL DEFAULT NULL,
    password_hash VARCHAR(255) NOT NULL,
    consent_agreed_at DATETIME NULL DEFAULT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    verification_photo VARCHAR(500) NULL DEFAULT NULL,
    verification_status ENUM('unverified','pending','approved','rejected') NOT NULL DEFAULT 'unverified',
    role VARCHAR(20) NULL DEFAULT 'Student',
    profile_pic_url VARCHAR(500) NULL DEFAULT NULL,
    fcm_token VARCHAR(500) NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rate_limits (
    bucket VARBINARY(255) NOT NULL PRIMARY KEY,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    window_start INT UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS verification_codes (
    email VARCHAR(160) NOT NULL PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    icon_res VARCHAR(50) DEFAULT 'ic_category_default',
    is_published TINYINT(1) DEFAULT 1,
    proposed_by BIGINT UNSIGNED,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_proposed_by FOREIGN KEY (proposed_by) REFERENCES users(id)
);

INSERT IGNORE INTO categories (name, icon_res, is_published) VALUES
    ('Food','ic_category_food',1),('Drink','ic_category_drink',1),('Tech','ic_category_tech',1),
    ('Electronics','ic_category_electronics',1),('Fashion','ic_category_fashion',1),('Books','ic_category_books',1),
    ('Repair','ic_category_repair',1),('Home','ic_category_home',1),('Laundry','ic_category_laundry',1),
    ('Delivery','ic_category_delivery',1),('Services','ic_category_services',1);

CREATE TABLE IF NOT EXISTS listings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    category VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    image_url VARCHAR(500),
    tags VARCHAR(255) NOT NULL DEFAULT '',
    free_slots VARCHAR(500) NULL DEFAULT NULL,
    major_id INT UNSIGNED NULL DEFAULT NULL,
    location VARCHAR(150),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    archived_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_listings_archived (archived_at),
    INDEX idx_listings_major (major_id),
    CONSTRAINT fk_listing_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS majors (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    faculty VARCHAR(120) NOT NULL DEFAULT '',
    is_published TINYINT(1) NOT NULL DEFAULT 1
);

INSERT IGNORE INTO majors (name, faculty) VALUES
    ('Computer Science', 'ICT'),
    ('Software Engineering', 'ICT'),
    ('Information Technology', 'ICT'),
    ('Electrical Engineering', 'Engineering'),
    ('Civil Engineering', 'Engineering'),
    ('Mechanical Engineering', 'Engineering'),
    ('Business Studies', 'Commerce'),
    ('Accountancy', 'Commerce'),
    ('Hospitality & Tourism', 'Commerce'),
    ('Graphic Design', 'Applied Sciences');

CREATE TABLE IF NOT EXISTS favorites (
    user_id BIGINT UNSIGNED NOT NULL,
    listing_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, listing_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS threads (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT UNSIGNED NOT NULL,
    buyer_id BIGINT UNSIGNED NOT NULL,
    seller_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_thread_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_seller FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    thread_id BIGINT UNSIGNED NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    text TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_thread FOREIGN KEY (thread_id) REFERENCES threads(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT UNSIGNED NOT NULL,
    buyer_id BIGINT UNSIGNED NOT NULL,
    seller_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status ENUM('offer_sent','accepted','pickup','completed','cancelled') NOT NULL DEFAULT 'offer_sent',
    impact_credited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_seller FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    body TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS verification_requests (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    verification_email VARCHAR(160) NOT NULL,
    status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_verification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    seller_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    listing_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reviewer_id BIGINT UNSIGNED NOT NULL,
    reviewer_name VARCHAR(120) NOT NULL,
    stars TINYINT UNSIGNED NOT NULL,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(80),
    reason VARCHAR(120) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- UGC moderation: users can block other users (Google Play UGC policy).
CREATE TABLE IF NOT EXISTS blocked_users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    blocked_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_block_pair (user_id, blocked_id),
    CONSTRAINT fk_block_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_block_target FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
);

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

CREATE TABLE IF NOT EXISTS security_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    thread_id BIGINT UNSIGNED NULL,
    listing_id BIGINT UNSIGNED NULL,
    landmark VARCHAR(120) DEFAULT NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_security_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_security_thread FOREIGN KEY (thread_id) REFERENCES threads(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL
);

INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES ('maintenance', '0');

-- ============================================================
-- Green Impact System (Phase: Sustainability)
-- TodoRays methodology: each completed re-sale displaces one new
-- item (displacement rate = 1). Coefficients are conservative and
-- per-category; unknown categories fall back to 'Other'.
-- ============================================================

CREATE TABLE IF NOT EXISTS sustainability_coefficients (
    id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL UNIQUE,
    co2_kg DECIMAL(8,2) NOT NULL DEFAULT 0,
    water_l DECIMAL(10,2) NOT NULL DEFAULT 0,
    paper_kg DECIMAL(8,2) NOT NULL DEFAULT 0,
    energy_kwh DECIMAL(8,2) NOT NULL DEFAULT 0
);

INSERT INTO sustainability_coefficients (category_name, co2_kg, water_l, paper_kg, energy_kwh) VALUES
('Food', 3.0, 120, 0.2, 4),
('Drink', 2.0, 90, 0.1, 3),
('Tech', 45.0, 280, 1.5, 75),
('Electronics', 55.0, 350, 1.5, 80),
('Fashion', 14.0, 1400, 0.1, 20),
('Books', 6.0, 30, 3.0, 8),
('Repair', 8.0, 60, 0.3, 12),
('Home', 20.0, 180, 2.5, 35),
('Laundry', 4.0, 150, 0.1, 5),
('Delivery', 3.0, 40, 0.3, 5),
('Services', 0, 0, 0, 0),
('Other', 5.0, 80, 0.5, 8)
ON DUPLICATE KEY UPDATE
    co2_kg = VALUES(co2_kg),
    water_l = VALUES(water_l),
    paper_kg = VALUES(paper_kg),
    energy_kwh = VALUES(energy_kwh);

CREATE TABLE IF NOT EXISTS user_impact (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    co2_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    water_l DECIMAL(12,2) NOT NULL DEFAULT 0,
    paper_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    energy_kwh DECIMAL(10,2) NOT NULL DEFAULT 0,
    impact_count INT UNSIGNED NOT NULL DEFAULT 0,
    tier ENUM('bronze','silver','gold','emerald') NOT NULL DEFAULT 'bronze',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_impact_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS impact_entries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    transaction_id BIGINT UNSIGNED NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    co2_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    water_l DECIMAL(12,2) NOT NULL DEFAULT 0,
    paper_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    energy_kwh DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_impact_entry_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_tx (user_id, transaction_id)
);

-- ============================================================
-- Alpha/Beta Bug Reporting (Release Beta track)
-- ============================================================

CREATE TABLE IF NOT EXISTS bug_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    description TEXT NOT NULL,
    device_model VARCHAR(120) NOT NULL DEFAULT '',
    os_version VARCHAR(30) NOT NULL DEFAULT '',
    app_version VARCHAR(30) NOT NULL DEFAULT '',
    screen VARCHAR(120) NOT NULL DEFAULT '',
    screenshot_url VARCHAR(500) NULL DEFAULT NULL,
    status ENUM('new','triaged','fixed') NOT NULL DEFAULT 'new',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bug_reporter FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

