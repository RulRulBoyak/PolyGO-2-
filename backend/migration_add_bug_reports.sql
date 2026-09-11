-- Alpha/beta bug-reporting mailbox for the release-beta track.
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