CREATE TABLE IF NOT EXISTS lab_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    report_id BIGINT NULL,
    teacher_id BIGINT NOT NULL,
    auto_score INT NULL,
    report_score INT NULL,
    manual_score INT NULL,
    final_score INT NOT NULL,
    comment VARCHAR(500) NULL,
    scored_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lab_score_submission (submission_id),
    KEY idx_lab_score_report_id (report_id),
    KEY idx_lab_score_teacher_id (teacher_id),
    CONSTRAINT fk_lab_score_submission
        FOREIGN KEY (submission_id) REFERENCES lab_submission(id),
    CONSTRAINT fk_lab_score_report
        FOREIGN KEY (report_id) REFERENCES lab_report(id)
);

CREATE TABLE IF NOT EXISTS lab_score_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    score_id BIGINT NOT NULL,
    old_final_score INT NOT NULL,
    new_final_score INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lab_score_change_log_score_id (score_id),
    KEY idx_lab_score_change_log_operator_id (operator_id),
    CONSTRAINT fk_lab_score_change_log_score
        FOREIGN KEY (score_id) REFERENCES lab_score(id) ON DELETE CASCADE
);
