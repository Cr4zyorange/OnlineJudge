CREATE TABLE IF NOT EXISTS lab_submission_source_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    lab_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_lab_submission_source_submission (submission_id),
    UNIQUE KEY uk_lab_submission_source_storage_key (storage_key),
    KEY idx_lab_submission_source_lab (lab_id),
    KEY idx_lab_submission_source_course (course_id),
    KEY idx_lab_submission_source_uploader (uploader_id),
    KEY idx_lab_submission_source_status (status),
    CONSTRAINT ck_lab_submission_source_size CHECK (file_size >= 0),
    CONSTRAINT ck_lab_submission_source_status CHECK (status IN ('AVAILABLE', 'DELETED')),
    CONSTRAINT fk_lab_submission_source_submission
        FOREIGN KEY (submission_id) REFERENCES lab_submission(id),
    CONSTRAINT fk_lab_submission_source_lab
        FOREIGN KEY (lab_id) REFERENCES lab_experiment(id)
);
