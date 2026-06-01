CREATE TABLE IF NOT EXISTS lab_evaluation_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    testcase_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    passed TINYINT(1) NOT NULL DEFAULT 0,
    score INT NOT NULL DEFAULT 0,
    actual_output TEXT NULL,
    message VARCHAR(500) NULL,
    executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lab_eval_submission (submission_id),
    KEY idx_lab_eval_testcase (testcase_id),
    CONSTRAINT fk_lab_eval_submission
        FOREIGN KEY (submission_id) REFERENCES lab_submission(id),
    CONSTRAINT fk_lab_eval_testcase
        FOREIGN KEY (testcase_id) REFERENCES lab_testcase(id)
);
