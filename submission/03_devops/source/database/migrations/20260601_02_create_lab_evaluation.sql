CREATE TABLE IF NOT EXISTS lab_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    passed_cases INT NOT NULL DEFAULT 0,
    total_cases INT NOT NULL DEFAULT 0,
    time_used_ms INT NULL,
    memory_used_kb INT NULL,
    feedback VARCHAR(500) NULL,
    compile_log TEXT NULL,
    run_log TEXT NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lab_evaluation_submission (submission_id),
    CONSTRAINT fk_lab_evaluation_submission
        FOREIGN KEY (submission_id) REFERENCES lab_submission(id)
);
