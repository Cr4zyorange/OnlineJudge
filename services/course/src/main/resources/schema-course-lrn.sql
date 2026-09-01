-- H2 test schema for Course-owned LRN facts (#355).  Production uses the
-- versioned MySQL migrations under database/migrations/course.
CREATE TABLE IF NOT EXISTS lrn_learning_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    deadline TIMESTAMP,
    progress INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    action_url VARCHAR(500),
    snapshot_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lrn_task_user_course ON lrn_learning_task(user_id, course_id);
CREATE INDEX IF NOT EXISTS idx_lrn_task_source ON lrn_learning_task(source_module, source_id);

CREATE TABLE IF NOT EXISTS lrn_learning_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    last_position VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lrn_progress_user_course_source UNIQUE(user_id, course_id, source_module, source_id)
);
CREATE INDEX IF NOT EXISTS idx_lrn_progress_user_course_chapter ON lrn_learning_progress(user_id, course_id, chapter_id);

CREATE TABLE IF NOT EXISTS lrn_learning_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    duration INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lrn_record_user_course_started ON lrn_learning_record(user_id, course_id, started_at);

CREATE TABLE IF NOT EXISTS lrn_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT,
    idempotency_key VARCHAR(128),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 1,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT,
    action_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_lrn_notification_idempotency_user UNIQUE(idempotency_key, user_id)
);
CREATE INDEX IF NOT EXISTS idx_lrn_notification_user_created ON lrn_notification(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_lrn_notification_user_read_created ON lrn_notification(user_id, is_read, created_at);

CREATE TABLE IF NOT EXISTS lrn_notification_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    old_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lrn_reminder_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reminder_type VARCHAR(32) NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    ahead_minutes INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lrn_reminder_rule_user_scope UNIQUE(user_id, reminder_type, source_module, ahead_minutes)
);

CREATE TABLE IF NOT EXISTS lrn_notification_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    enable_experiment BOOLEAN NOT NULL DEFAULT TRUE,
    enable_homework BOOLEAN NOT NULL DEFAULT TRUE,
    enable_grade BOOLEAN NOT NULL DEFAULT TRUE,
    enable_announcement BOOLEAN NOT NULL DEFAULT TRUE,
    enable_non_critical_reminder BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lrn_notification_setting_user UNIQUE(user_id)
);

-- Durable inbox for #306 frozen Assessment/Grade facts.  event_id is the
-- idempotency key: replaying a broker delivery never re-applies the fact.
CREATE TABLE IF NOT EXISTS course_learning_event_inbox (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Watermark advanced only by a complete course.membership.snapshot.v2 fact.
CREATE TABLE IF NOT EXISTS course_learning_membership_watermark (
    course_id BIGINT PRIMARY KEY,
    snapshot_version BIGINT NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
