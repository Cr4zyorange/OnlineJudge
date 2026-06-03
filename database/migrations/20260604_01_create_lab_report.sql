CREATE TABLE IF NOT EXISTS lab_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lab_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    submission_id BIGINT NULL,
    file_id VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    submit_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    score INT NULL,
    comment VARCHAR(1000) NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scored_by BIGINT NULL,
    scored_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lab_report_lab_id (lab_id),
    KEY idx_lab_report_student_id (student_id),
    KEY idx_lab_report_submission_id (submission_id),
    KEY idx_lab_report_submitted_at (submitted_at),
    UNIQUE KEY uk_lab_report_version (lab_id, student_id, version),
    CONSTRAINT fk_lab_report_lab
        FOREIGN KEY (lab_id) REFERENCES lab_experiment(id),
    CONSTRAINT fk_lab_report_submission
        FOREIGN KEY (submission_id) REFERENCES lab_submission(id)
);
