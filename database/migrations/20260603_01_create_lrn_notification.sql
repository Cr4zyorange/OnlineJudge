CREATE TABLE IF NOT EXISTS lrn_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    idempotency_key VARCHAR(128) NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 1,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NULL,
    action_url VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_lrn_notification_idempotency_user (idempotency_key, user_id),
    KEY idx_lrn_notification_user_created (user_id, created_at),
    KEY idx_lrn_notification_user_type_created (user_id, type, created_at),
    KEY idx_lrn_notification_user_read_created (user_id, is_read, created_at),
    KEY idx_lrn_notification_course (course_id)
);

CREATE TABLE IF NOT EXISTS lrn_notification_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    old_status VARCHAR(32) NULL,
    new_status VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_notification_status_notification (notification_id),
    KEY idx_lrn_notification_status_user_time (user_id, operated_at),
    KEY idx_lrn_notification_status_operation (operation_type)
);
