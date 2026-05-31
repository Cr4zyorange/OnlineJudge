CREATE TABLE IF NOT EXISTS crs_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    resource_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    publish_at DATETIME NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    upload_user_id BIGINT NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_hwk_homework (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'FILE',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    total_score DECIMAL(6, 2) NOT NULL DEFAULT 100.00,
    deadline DATETIME NULL,
    allow_resubmit TINYINT(1) NOT NULL DEFAULT 0,
    allow_late_submit TINYINT(1) NOT NULL DEFAULT 0,
    show_evaluation_before_publish TINYINT(1) NOT NULL DEFAULT 0,
    judge_config_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    published_at DATETIME NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
