CREATE TABLE IF NOT EXISTS lab_experiment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    deadline DATETIME NOT NULL,
    max_score INT NOT NULL DEFAULT 100,
    attachment_ids VARCHAR(500) NULL,
    allowed_languages VARCHAR(255) NULL,
    evaluation_mode VARCHAR(32) NOT NULL DEFAULT 'DOCKER_IO',
    auto_evaluate TINYINT(1) NOT NULL DEFAULT 1,
    report_required TINYINT(1) NOT NULL DEFAULT 0,
    time_limit_ms INT NOT NULL DEFAULT 60000,
    memory_limit_kb INT NOT NULL DEFAULT 262144,
    created_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lab_experiment_course (course_id),
    KEY idx_lab_experiment_status (status),
    KEY idx_lab_experiment_deadline (deadline),
    KEY idx_lab_experiment_created_by (created_by)
);

CREATE TABLE IF NOT EXISTS lab_testcase (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lab_id BIGINT NOT NULL,
    input TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    score_weight INT NOT NULL DEFAULT 0,
    is_public TINYINT(1) NOT NULL DEFAULT 0,
    time_limit_ms INT NOT NULL DEFAULT 60000,
    memory_limit_kb INT NOT NULL DEFAULT 262144,
    order_num INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lab_testcase_lab_id (lab_id),
    KEY idx_lab_testcase_order_num (order_num),
    CONSTRAINT fk_lab_testcase_lab
        FOREIGN KEY (lab_id) REFERENCES lab_experiment(id)
);
