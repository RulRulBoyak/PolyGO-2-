CREATE TABLE IF NOT EXISTS pulse_likes (
    pulse_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (pulse_id, user_id),
    KEY idx_pulse_likes_user (user_id),
    CONSTRAINT fk_pulse_likes_post FOREIGN KEY (pulse_id) REFERENCES campus_alerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_pulse_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pulse_comments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pulse_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    body VARCHAR(500) NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pulse_comments_post (pulse_id, is_deleted, id),
    KEY idx_pulse_comments_user (user_id),
    CONSTRAINT fk_pulse_comments_post FOREIGN KEY (pulse_id) REFERENCES campus_alerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_pulse_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
