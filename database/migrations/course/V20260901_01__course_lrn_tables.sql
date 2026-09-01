-- #355 Course-owned LRN facts.  LRN is folded into Course: notifications,
-- reminders, learning tasks/records/progress and the fact inbox live in the
-- course schema with the same names as the monolith lrn_* tables so existing
-- API and browser behavior keeps working.
CREATE TABLE IF NOT EXISTS lrn_learning_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    deadline DATETIME NULL,
    progress INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    action_url VARCHAR(500) NULL,
    snapshot_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_task_user_course (user_id, course_id),
    KEY idx_lrn_task_user_type_status_deadline (user_id, task_type, status, deadline),
    KEY idx_lrn_task_status_deadline (status, deadline),
    KEY idx_lrn_task_source (source_module, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lrn_learning_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    last_position VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_progress_user_course_source (user_id, course_id, source_module, source_id),
    KEY idx_lrn_progress_user_course_chapter (user_id, course_id, chapter_id),
    KEY idx_lrn_progress_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lrn_learning_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    duration INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_record_user_started (user_id, started_at),
    KEY idx_lrn_record_user_course_started (user_id, course_id, started_at),
    KEY idx_lrn_record_rate_limit (user_id, course_id, source_module, source_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Durable inbox for #306 frozen Assessment/Grade facts.  event_id is the
-- idempotency key: replaying a broker delivery never re-applies the fact.
CREATE TABLE IF NOT EXISTS course_learning_event_inbox (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Watermark advanced only by a complete course.membership.snapshot.v2 fact.
CREATE TABLE IF NOT EXISTS course_learning_membership_watermark (
    course_id BIGINT NOT NULL PRIMARY KEY,
    snapshot_version BIGINT NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_learning_roster_watermark_version (snapshot_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
