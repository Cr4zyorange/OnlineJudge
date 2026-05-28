CREATE TABLE IF NOT EXISTS t_hwk_homework (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    description CLOB NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_score DECIMAL(6,2) NOT NULL,
    deadline DATETIME NOT NULL,
    allow_resubmit BOOLEAN NOT NULL DEFAULT TRUE,
    allow_late_submit BOOLEAN NOT NULL DEFAULT FALSE,
    show_evaluation_before_publish BOOLEAN NOT NULL DEFAULT FALSE,
    judge_config_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    published_at DATETIME NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_hwk_homework_total_score CHECK (total_score > 0)
);

CREATE INDEX IF NOT EXISTS idx_hwk_homework_course
    ON t_hwk_homework (course_id, status, is_deleted, deadline);

CREATE INDEX IF NOT EXISTS idx_hwk_homework_created_by
    ON t_hwk_homework (created_by, status);

CREATE TABLE IF NOT EXISTS t_hwk_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    homework_id BIGINT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    stem CLOB NOT NULL,
    options_json CLOB NOT NULL,
    answer_json CLOB NOT NULL,
    score DECIMAL(6,2) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_hwk_question_homework FOREIGN KEY (homework_id) REFERENCES t_hwk_homework (id),
    CONSTRAINT ck_hwk_question_score CHECK (score >= 0)
);

CREATE INDEX IF NOT EXISTS idx_hwk_question_homework
    ON t_hwk_question (homework_id, sort_order);

CREATE TABLE IF NOT EXISTS t_hwk_test_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    homework_id BIGINT NOT NULL,
    input_data CLOB NOT NULL,
    expected_output CLOB NOT NULL,
    score_weight DECIMAL(6,2) NOT NULL,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    time_limit_ms INT NOT NULL,
    memory_limit_kb INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_hwk_test_case_homework FOREIGN KEY (homework_id) REFERENCES t_hwk_homework (id),
    CONSTRAINT ck_hwk_test_case_weight CHECK (score_weight >= 0),
    CONSTRAINT ck_hwk_test_case_time CHECK (time_limit_ms > 0),
    CONSTRAINT ck_hwk_test_case_memory CHECK (memory_limit_kb > 0)
);

CREATE INDEX IF NOT EXISTS idx_hwk_test_case_homework
    ON t_hwk_test_case (homework_id, sort_order);

CREATE TABLE IF NOT EXISTS t_hwk_judge_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    homework_id BIGINT NOT NULL,
    language_limit_json CLOB NULL,
    time_limit_ms INT NOT NULL,
    memory_limit_kb INT NOT NULL,
    output_compare_mode VARCHAR(32) NOT NULL DEFAULT 'EXACT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_hwk_judge_config_homework FOREIGN KEY (homework_id) REFERENCES t_hwk_homework (id)
);

CREATE TABLE IF NOT EXISTS t_hwk_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    homework_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    submit_type VARCHAR(32) NOT NULL,
    answer_text CLOB NULL,
    answer_json CLOB NULL,
    file_url VARCHAR(500) NULL,
    language VARCHAR(32) NULL,
    submit_status VARCHAR(32) NOT NULL,
    evaluation_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    review_status VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED',
    auto_score DECIMAL(6,2) NULL,
    manual_score DECIMAL(6,2) NULL,
    final_score DECIMAL(6,2) NULL,
    comment VARCHAR(1000) NULL,
    is_latest BOOLEAN NOT NULL DEFAULT TRUE,
    is_final BOOLEAN NOT NULL DEFAULT TRUE,
    submitted_at DATETIME NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_hwk_submission_homework FOREIGN KEY (homework_id) REFERENCES t_hwk_homework (id)
);

CREATE INDEX IF NOT EXISTS idx_hwk_submission_homework_student
    ON t_hwk_submission (homework_id, student_id, is_latest, is_final, submitted_at);

CREATE INDEX IF NOT EXISTS idx_hwk_submission_status
    ON t_hwk_submission (homework_id, submit_status, evaluation_status, review_status);

CREATE TABLE IF NOT EXISTS t_hwk_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    homework_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    evaluator_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    score DECIMAL(6,2) NULL,
    total_score DECIMAL(6,2) NOT NULL,
    passed_count INT NOT NULL DEFAULT 0,
    total_count INT NOT NULL DEFAULT 0,
    case_results_json CLOB NULL,
    message VARCHAR(1000) NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_hwk_evaluation_homework FOREIGN KEY (homework_id) REFERENCES t_hwk_homework (id),
    CONSTRAINT fk_hwk_evaluation_submission FOREIGN KEY (submission_id) REFERENCES t_hwk_submission (id)
);

CREATE INDEX IF NOT EXISTS idx_hwk_evaluation_submission
    ON t_hwk_evaluation (submission_id, created_at);

CREATE INDEX IF NOT EXISTS idx_hwk_evaluation_status
    ON t_hwk_evaluation (homework_id, status);
