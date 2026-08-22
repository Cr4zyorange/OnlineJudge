CREATE TABLE IF NOT EXISTS t_hwk_submission_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    submission_id BIGINT NULL,
    homework_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    active_slot TINYINT NULL,
    expires_at DATETIME NULL,
    bound_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    KEY idx_hwk_attachment_homework (homework_id),
    KEY idx_hwk_attachment_course (course_id),
    KEY idx_hwk_attachment_uploader (uploader_id),
    KEY idx_hwk_attachment_cleanup (status, expires_at),
    KEY idx_hwk_attachment_deleted_cleanup (status, deleted_at),
    CONSTRAINT uk_hwk_attachment_public_id UNIQUE (public_id),
    CONSTRAINT uk_hwk_attachment_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_hwk_attachment_submission UNIQUE (submission_id),
    CONSTRAINT uk_hwk_attachment_active_slot UNIQUE (homework_id, uploader_id, active_slot),
    CONSTRAINT ck_hwk_attachment_file_size CHECK (file_size > 0),
    CONSTRAINT ck_hwk_attachment_status
        CHECK (status IN ('UPLOADED', 'BOUND', 'DELETED')),
    CONSTRAINT ck_hwk_attachment_lifecycle CHECK (
        (status = 'UPLOADED'
            AND active_slot = 1
            AND submission_id IS NULL
            AND expires_at IS NOT NULL
            AND bound_at IS NULL
            AND deleted_at IS NULL)
        OR (status = 'BOUND'
            AND active_slot IS NULL
            AND submission_id IS NOT NULL
            AND expires_at IS NULL
            AND bound_at IS NOT NULL
            AND deleted_at IS NULL)
        OR (status = 'DELETED'
            AND active_slot IS NULL
            AND submission_id IS NULL
            AND expires_at IS NULL
            AND bound_at IS NULL
            AND deleted_at IS NOT NULL)
    ),
    CONSTRAINT fk_hwk_attachment_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id) ON DELETE CASCADE,
    CONSTRAINT fk_hwk_attachment_submission
        FOREIGN KEY (submission_id) REFERENCES t_hwk_submission(id) ON DELETE CASCADE
);
