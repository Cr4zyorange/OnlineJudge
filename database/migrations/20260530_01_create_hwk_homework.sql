CREATE TABLE IF NOT EXISTS t_hwk_homework (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    total_score DECIMAL(6,2) NOT NULL DEFAULT 100,
    deadline DATETIME NOT NULL,
    allow_resubmit TINYINT(1) NOT NULL DEFAULT 1,
    allow_late_submit TINYINT(1) NOT NULL DEFAULT 0,
    show_evaluation_before_publish TINYINT(1) NOT NULL DEFAULT 0,
    judge_config_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    published_at DATETIME NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_hwk_homework_course (course_id),
    KEY idx_hwk_homework_status (status),
    KEY idx_hwk_homework_deadline (deadline),
    KEY idx_hwk_homework_created_by (created_by)
);

CREATE TABLE IF NOT EXISTS t_hwk_judge_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    language_limit_json TEXT NULL,
    time_limit_ms INT NOT NULL DEFAULT 1000,
    memory_limit_kb INT NOT NULL DEFAULT 65536,
    output_compare_mode VARCHAR(32) NOT NULL DEFAULT 'EXACT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hwk_judge_config_homework (homework_id),
    KEY idx_hwk_judge_config_homework (homework_id),
    CONSTRAINT fk_hwk_judge_config_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS t_hwk_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    stem TEXT NOT NULL,
    options_json TEXT NULL,
    answer_json TEXT NOT NULL,
    score DECIMAL(6,2) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_hwk_question_homework (homework_id),
    KEY idx_hwk_question_order (sort_order),
    CONSTRAINT fk_hwk_question_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id)
);

CREATE TABLE IF NOT EXISTS t_hwk_test_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    score_weight DECIMAL(6,2) NOT NULL DEFAULT 0,
    is_hidden TINYINT(1) NOT NULL DEFAULT 0,
    time_limit_ms INT NOT NULL DEFAULT 1000,
    memory_limit_kb INT NOT NULL DEFAULT 65536,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_hwk_test_case_homework (homework_id),
    KEY idx_hwk_test_case_order (sort_order),
    CONSTRAINT fk_hwk_test_case_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id)
);
