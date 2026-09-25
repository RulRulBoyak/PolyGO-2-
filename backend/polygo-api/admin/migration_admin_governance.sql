-- Apply once before deploying the governance-enabled admin panel.
-- These tables deliberately remain outside Android-owned app tables.
CREATE TABLE IF NOT EXISTS admin_report_actions (
    report_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    status ENUM('pending','resolved','dismissed') NOT NULL DEFAULT 'pending',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    action_name VARCHAR(80) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT UNSIGNED NULL,
    details VARCHAR(500) NULL,
    actor_label VARCHAR(120) NOT NULL DEFAULT 'shared-admin',
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_audit_created (created_at),
    INDEX idx_admin_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

