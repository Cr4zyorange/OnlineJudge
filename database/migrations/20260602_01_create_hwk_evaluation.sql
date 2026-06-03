CREATE TABLE IF NOT EXISTS t_hwk_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    score DECIMAL(6,2) NOT NULL DEFAULT 0,
    passed_cases INT NOT NULL DEFAULT 0,
    total_cases INT NOT NULL DEFAULT 0,
    duration_ms INT NULL,
    error_message TEXT NULL,
    feedback TEXT NULL,
    compile_log TEXT NULL,
    run_log TEXT NULL,
    reevaluation TINYINT(1) NOT NULL DEFAULT 0,
    triggered_by BIGINT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_hwk_evaluation_submission (submission_id),
    KEY idx_hwk_evaluation_status (status),
    KEY idx_hwk_evaluation_started_at (started_at),
    CONSTRAINT fk_hwk_evaluation_submission
        FOREIGN KEY (submission_id) REFERENCES t_hwk_submission(id) ON DELETE CASCADE
);
