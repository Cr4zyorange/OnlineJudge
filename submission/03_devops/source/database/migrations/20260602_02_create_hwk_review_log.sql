CREATE TABLE IF NOT EXISTS t_hwk_review_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    homework_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    old_score DECIMAL(6,2) NULL,
    new_score DECIMAL(6,2) NULL,
    comment VARCHAR(1000) NULL,
    operator_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_hwk_review_log_submission (submission_id),
    KEY idx_hwk_review_log_homework_student (homework_id, student_id),
    KEY idx_hwk_review_log_operation (operation_type),
    CONSTRAINT fk_hwk_review_log_submission
        FOREIGN KEY (submission_id) REFERENCES t_hwk_submission(id) ON DELETE CASCADE,
    CONSTRAINT fk_hwk_review_log_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id) ON DELETE CASCADE
);
