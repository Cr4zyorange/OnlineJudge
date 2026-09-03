CREATE TABLE IF NOT EXISTS lrn_reminder_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reminder_type VARCHAR(32) NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    ahead_minutes INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_reminder_rule_user_scope (user_id, reminder_type, source_module, ahead_minutes),
    KEY idx_lrn_reminder_rule_user (user_id),
    KEY idx_lrn_reminder_rule_type_enabled (reminder_type, source_module, enabled)
);

CREATE TABLE IF NOT EXISTS lrn_notification_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    enable_experiment BOOLEAN NOT NULL DEFAULT TRUE,
    enable_homework BOOLEAN NOT NULL DEFAULT TRUE,
    enable_grade BOOLEAN NOT NULL DEFAULT TRUE,
    enable_announcement BOOLEAN NOT NULL DEFAULT TRUE,
    enable_non_critical_reminder BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_notification_setting_user (user_id)
);

CREATE TABLE IF NOT EXISTS lrn_reminder_scan_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    scan_started_at DATETIME NOT NULL,
    scan_ended_at DATETIME NULL,
    triggered_count INT NOT NULL DEFAULT 0,
    failed_reason VARCHAR(500) NULL,
    retry_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_reminder_scan_batch (batch_id),
    KEY idx_lrn_reminder_scan_started (scan_started_at),
    KEY idx_lrn_reminder_scan_retry (retry_status)
);
