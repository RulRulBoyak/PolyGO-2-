-- Campus planner: apply once to existing PolyGo databases.
ALTER TABLE categories MODIFY icon_res VARCHAR(50) DEFAULT 'ic_category_tech';
UPDATE categories SET icon_res = 'ic_category_tech'
WHERE icon_res IN ('ic_category_default', 'ic_category_electronics');
UPDATE categories SET icon_res = 'ic_category_service'
WHERE icon_res = 'ic_category_services';

CREATE TABLE IF NOT EXISTS campus_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    venue VARCHAR(120) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NULL,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_campus_events_published_starts (is_published, starts_at)
);

CREATE TABLE IF NOT EXISTS timetable_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    course VARCHAR(100) NOT NULL,
    room VARCHAR(80) NOT NULL,
    day_of_week TINYINT UNSIGNED NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_timetable_entries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_timetable_entries_user_day (user_id, day_of_week, starts_at)
);
