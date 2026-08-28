-- MySQL bootstrap schema for Docker Compose.
-- Generated from database/migrations in Compose startup order; run once by the MySQL container on an empty mysql-data volume.
SET NAMES utf8mb4;

CREATE TABLE schema_migrations (
    installed_rank BIGINT NOT NULL AUTO_INCREMENT,
    version VARCHAR(255) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    installed_type VARCHAR(16) NOT NULL,
    execution_ms BIGINT NOT NULL DEFAULT 0,
    installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (installed_rank),
    UNIQUE KEY uk_schema_migrations_version (version)
);


-- Source: database/migrations/DB-AUTH-01-auth-user-session.sql
CREATE TABLE IF NOT EXISTS t_auth_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    user_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    avatar_url VARCHAR(255) NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(128) NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_auth_user_username UNIQUE (username),
    CONSTRAINT uk_auth_user_phone UNIQUE (phone),
    CONSTRAINT uk_auth_user_email UNIQUE (email)
);

CREATE INDEX idx_auth_user_type
    ON t_auth_user (user_type);

CREATE INDEX idx_auth_user_status
    ON t_auth_user (account_status);

CREATE TABLE IF NOT EXISTS t_auth_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (role_id),
    CONSTRAINT uk_auth_role_code UNIQUE (role_code)
);

CREATE INDEX idx_auth_role_enabled
    ON t_auth_role (enabled);

CREATE TABLE IF NOT EXISTS t_auth_permission (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    module_code VARCHAR(32) NOT NULL,
    resource_pattern VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (permission_id),
    CONSTRAINT uk_auth_permission_code UNIQUE (permission_code)
);

CREATE INDEX idx_auth_permission_type
    ON t_auth_permission (permission_type);

CREATE INDEX idx_auth_permission_module
    ON t_auth_permission (module_code);

CREATE TABLE IF NOT EXISTS t_auth_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_by BIGINT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_auth_user_role_user FOREIGN KEY (user_id) REFERENCES t_auth_user (user_id),
    CONSTRAINT fk_auth_user_role_role FOREIGN KEY (role_id) REFERENCES t_auth_role (role_id)
);

CREATE INDEX idx_auth_user_role_user
    ON t_auth_user_role (user_id);

CREATE INDEX idx_auth_user_role_role
    ON t_auth_user_role (role_id);

CREATE TABLE IF NOT EXISTS t_auth_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    assigned_by BIGINT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_auth_role_permission_role FOREIGN KEY (role_id) REFERENCES t_auth_role (role_id),
    CONSTRAINT fk_auth_role_permission_permission FOREIGN KEY (permission_id) REFERENCES t_auth_permission (permission_id)
);

CREATE INDEX idx_auth_role_permission_role
    ON t_auth_role_permission (role_id);

CREATE INDEX idx_auth_role_permission_permission
    ON t_auth_role_permission (permission_id);

CREATE TABLE IF NOT EXISTS t_auth_session (
    session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_id VARCHAR(128) NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VALID',
    PRIMARY KEY (session_id),
    CONSTRAINT uk_auth_session_token UNIQUE (token_id),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES t_auth_user (user_id)
);

CREATE INDEX idx_auth_session_user
    ON t_auth_session (user_id);

CREATE INDEX idx_auth_session_expires
    ON t_auth_session (expires_at);

CREATE INDEX idx_auth_session_status
    ON t_auth_session (status);

CREATE TABLE IF NOT EXISTS t_auth_audit_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id BIGINT NULL,
    operation_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(64) NULL,
    result_status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id)
);

CREATE INDEX idx_auth_audit_operator
    ON t_auth_audit_log (operator_id);

CREATE INDEX idx_auth_audit_operation
    ON t_auth_audit_log (operation_type);

CREATE INDEX idx_auth_audit_result
    ON t_auth_audit_log (result_status);

CREATE INDEX idx_auth_audit_created
    ON t_auth_audit_log (created_at);


-- Source: database/migrations/DB-CRS-01-course-and-member.sql
CREATE TABLE IF NOT EXISTS crs_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    teacher_id BIGINT NOT NULL,
    semester VARCHAR(64) NULL,
    category VARCHAR(64) NULL,
    cover_url VARCHAR(500) NULL,
    enrollment_mode VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    invite_code VARCHAR(64) NULL,
    max_students INT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_course_teacher (teacher_id),
    INDEX idx_crs_course_status (status),
    INDEX idx_crs_course_name (course_name)
);

CREATE TABLE IF NOT EXISTS crs_course_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    join_method VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    join_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    apply_reason VARCHAR(500) NULL,
    approved_by BIGINT NULL,
    joined_at DATETIME NULL,
    left_at DATETIME NULL,
    last_access_at DATETIME NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_crs_course_member (course_id, user_id),
    INDEX idx_crs_member_course (course_id),
    INDEX idx_crs_member_user (user_id),
    INDEX idx_crs_member_role_status (role, join_status),
    CONSTRAINT fk_crs_member_course FOREIGN KEY (course_id) REFERENCES crs_course (id)
);


-- Source: database/migrations/DB-CRS-02-course-chapter.sql
CREATE TABLE IF NOT EXISTS crs_chapter (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    chapter_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    objective TEXT NULL,
    visible_status TINYINT(1) NOT NULL DEFAULT 1,
    chapter_type TINYINT(1) NOT NULL DEFAULT 1,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_chapter_course_parent_order (course_id, parent_id, sort_order),
    INDEX idx_crs_chapter_parent (parent_id),
    CONSTRAINT fk_crs_chapter_course FOREIGN KEY (course_id) REFERENCES crs_course (id),
    CONSTRAINT fk_crs_chapter_parent FOREIGN KEY (parent_id) REFERENCES crs_chapter (id)
);


-- Source: database/migrations/DB-CRS-03-course-resource.sql
CREATE TABLE IF NOT EXISTS crs_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    resource_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    publish_at DATETIME NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    upload_user_id BIGINT NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_resource_course (course_id),
    INDEX idx_crs_resource_chapter (chapter_id),
    INDEX idx_crs_resource_uploader (upload_user_id),
    CONSTRAINT fk_crs_resource_course FOREIGN KEY (course_id) REFERENCES crs_course (id),
    CONSTRAINT fk_crs_resource_chapter FOREIGN KEY (chapter_id) REFERENCES crs_chapter (id)
);


-- Source: database/migrations/DB-CRS-05-course-announcement.sql
CREATE TABLE IF NOT EXISTS crs_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_top TINYINT(1) NOT NULL DEFAULT 0,
    publisher_id BIGINT NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_announcement_course_top_time (course_id, is_top, created_at),
    INDEX idx_crs_announcement_publisher (publisher_id),
    CONSTRAINT fk_crs_announcement_course FOREIGN KEY (course_id) REFERENCES crs_course (id)
);


-- Source: database/migrations/20260525_01_create_grd_grade_item.sql
CREATE TABLE IF NOT EXISTS t_grade_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NULL,
    full_score DECIMAL(6,2) NOT NULL,
    weight DECIMAL(6,4) NOT NULL,
    included_in_final BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_grade_item_full_score CHECK (full_score > 0),
    CONSTRAINT ck_grade_item_weight CHECK (weight >= 0 AND weight <= 1)
);

CREATE INDEX idx_grade_item_course
    ON t_grade_item (course_id, enabled, deleted, sort_order);

CREATE INDEX idx_grade_item_source
    ON t_grade_item (source_type, source_id);

CREATE TABLE IF NOT EXISTS t_grade_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    grade_item_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NULL,
    raw_score DECIMAL(6,2) NULL,
    weighted_score DECIMAL(6,2) NULL,
    grade_status VARCHAR(30) NOT NULL,
    publish_status VARCHAR(30) NOT NULL DEFAULT 'UNPUBLISHED',
    comment VARCHAR(1000) NULL,
    source_updated_at DATETIME NULL,
    calculated_at DATETIME NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_grade_record_student_item UNIQUE (course_id, student_id, grade_item_id)
);

CREATE INDEX idx_grade_record_course_status
    ON t_grade_record (course_id, grade_status, publish_status);

CREATE INDEX idx_grade_record_student_publish
    ON t_grade_record (course_id, student_id, publish_status);

CREATE TABLE IF NOT EXISTS t_course_grade_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    final_score DECIMAL(6,2) NULL,
    final_status VARCHAR(30) NOT NULL,
    publish_status VARCHAR(30) NOT NULL DEFAULT 'UNPUBLISHED',
    calculation_batch_id BIGINT NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_grade_student UNIQUE (course_id, student_id)
);

CREATE INDEX idx_course_grade_publish
    ON t_course_grade_summary (course_id, publish_status);

CREATE TABLE IF NOT EXISTS t_grade_publish_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    publish_scope VARCHAR(30) NOT NULL,
    published_count INT NOT NULL DEFAULT 0,
    published_by BIGINT NOT NULL,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notification_status VARCHAR(30) NOT NULL,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_grade_publish_record_course
    ON t_grade_publish_record (course_id, published_at);

CREATE UNIQUE INDEX uk_grade_publish_record_idempotency
    ON t_grade_publish_record (course_id, idempotency_key);

CREATE TABLE IF NOT EXISTS t_grade_calculation_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    affected_item_count INT NOT NULL DEFAULT 0,
    affected_student_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    message VARCHAR(1000) NULL,
    calculated_by BIGINT NOT NULL,
    calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_grade_calculation_batch_course
    ON t_grade_calculation_batch (course_id, calculated_at);

CREATE TABLE IF NOT EXISTS t_grade_change_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    grade_item_id BIGINT NULL,
    change_type VARCHAR(30) NOT NULL,
    old_value DECIMAL(6,2) NULL,
    new_value DECIMAL(6,2) NULL,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_grade_change_log_course
    ON t_grade_change_log (course_id, student_id, grade_item_id, created_at);

CREATE TABLE IF NOT EXISTS t_grade_review_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    grade_item_id BIGINT NULL,
    target_type VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    original_score DECIMAL(6,2) NULL,
    adjusted_score DECIMAL(6,2) NULL,
    response_comment VARCHAR(1000) NULL,
    submitted_at DATETIME NOT NULL,
    processed_by BIGINT NULL,
    processed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_grade_review_course_status
    ON t_grade_review_request (course_id, status);

CREATE INDEX idx_grade_review_student_status
    ON t_grade_review_request (course_id, student_id, status);

CREATE INDEX idx_grade_review_target_pending
    ON t_grade_review_request (course_id, student_id, target_type, grade_item_id, status);

CREATE TABLE IF NOT EXISTS t_grade_analysis_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    grade_item_id BIGINT NULL,
    source_data_time DATETIME NOT NULL,
    source_fingerprint VARCHAR(96) NULL,
    average_score DECIMAL(6,2) NULL,
    max_score DECIMAL(6,2) NULL,
    min_score DECIMAL(6,2) NULL,
    pass_rate DECIMAL(6,4) NULL,
    completion_rate DECIMAL(6,4) NULL,
    total_student_count INT NULL,
    completed_count INT NULL,
    missing_count INT NULL,
    unsubmitted_count INT NULL,
    ungraded_count INT NULL,
    distribution_json TEXT NULL,
    generated_by BIGINT NOT NULL,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_grade_analysis_snapshot_course
    ON t_grade_analysis_snapshot (course_id, target_type, grade_item_id, generated_at);

CREATE INDEX idx_grade_analysis_snapshot_source
    ON t_grade_analysis_snapshot (
        course_id, target_type, grade_item_id, source_fingerprint, generated_at
    );

CREATE TABLE IF NOT EXISTS t_grade_analysis_source_version (
    course_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    grade_item_key BIGINT NOT NULL,
    source_version BIGINT NOT NULL,
    source_data_time DATETIME NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (course_id, target_type, grade_item_key)
);

CREATE INDEX idx_grade_analysis_source_version_updated
    ON t_grade_analysis_source_version (updated_at);


-- Source: database/migrations/20260525_02_create_lab_experiment.sql
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
    published_at DATETIME NULL,
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


-- Source: database/migrations/20260526_01_create_lab_submission.sql
CREATE TABLE IF NOT EXISTS lab_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lab_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    code_content TEXT NULL,
    file_id VARCHAR(128) NULL,
    language VARCHAR(20) NOT NULL,
    submit_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    evaluation_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    final_score INT NULL,
    auto_score INT NULL,
    version INT NOT NULL DEFAULT 1,
    is_final TINYINT(1) NOT NULL DEFAULT 1,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    KEY idx_sub_lab_id (lab_id),
    KEY idx_sub_student_id (student_id),
    KEY idx_sub_submit_status (submit_status),
    KEY idx_sub_evaluation_status (evaluation_status),
    KEY idx_submitted_at (submitted_at),
    UNIQUE KEY uk_lab_student_version (lab_id, student_id, version),
    CONSTRAINT fk_lab_submission_lab
        FOREIGN KEY (lab_id) REFERENCES lab_experiment(id)
);


-- Source: database/migrations/20260822_02_create_lab_submission_source_file.sql
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


-- Source: database/migrations/20260530_01_create_hwk_homework.sql
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


-- Source: database/migrations/20260530_01_create_lrn_learning_task.sql
CREATE TABLE IF NOT EXISTS lrn_learning_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    deadline DATETIME NULL,
    progress INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    action_url VARCHAR(500) NULL,
    snapshot_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_task_user_course (user_id, course_id),
    KEY idx_lrn_task_user_type_status_deadline (user_id, task_type, status, deadline),
    KEY idx_lrn_task_status_deadline (status, deadline),
    KEY idx_lrn_task_source (source_module, source_id)
);


-- Source: database/migrations/20260531_01_create_lrn_learning_progress.sql
CREATE TABLE IF NOT EXISTS lrn_learning_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    last_position VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_progress_user_course_source (user_id, course_id, source_module, source_id),
    KEY idx_lrn_progress_user_course_chapter (user_id, course_id, chapter_id),
    KEY idx_lrn_progress_updated_at (updated_at)
);


-- Source: database/migrations/20260601_01_create_hwk_submission.sql
CREATE TABLE IF NOT EXISTS t_hwk_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    submit_type VARCHAR(20) NOT NULL,
    answer_text TEXT NULL,
    answer_json TEXT NULL,
    file_url TEXT NULL,
    language VARCHAR(32) NULL,
    submit_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    evaluation_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    review_status VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED',
    auto_score DECIMAL(6,2) NULL,
    manual_score DECIMAL(6,2) NULL,
    final_score DECIMAL(6,2) NULL,
    comment TEXT NULL,
    version INT NOT NULL DEFAULT 1,
    is_final TINYINT(1) NOT NULL DEFAULT 1,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    KEY idx_hwk_submission_homework (homework_id),
    KEY idx_hwk_submission_student (student_id),
    KEY idx_hwk_submission_status (submit_status),
    KEY idx_hwk_submission_evaluation (evaluation_status),
    KEY idx_hwk_submission_review (review_status),
    KEY idx_hwk_submission_submitted_at (submitted_at),
    KEY idx_hwk_submission_effective (homework_id, is_final, is_deleted, submit_status, student_id),
    KEY idx_hwk_submission_attention (
        homework_id, is_final, is_deleted, submitted_at, id,
        submit_status, student_id, submit_type, evaluation_status, review_status
    ),
    UNIQUE KEY uk_hwk_submission_version (homework_id, student_id, version),
    CONSTRAINT fk_hwk_submission_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id) ON DELETE CASCADE
);


-- Source: database/migrations/20260601_01_create_lab_evaluation_result.sql
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


-- Source: database/migrations/20260601_02_create_lab_evaluation.sql
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


-- Source: database/migrations/20260602_01_create_hwk_evaluation.sql
CREATE TABLE IF NOT EXISTS t_hwk_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    homework_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    evaluation_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    score DECIMAL(6,2) NOT NULL DEFAULT 0,
    passed_cases INT NOT NULL DEFAULT 0,
    total_cases INT NOT NULL DEFAULT 0,
    time_used_ms INT NULL,
    memory_used_kb INT NULL,
    error_message TEXT NULL,
    feedback TEXT NULL,
    log_url VARCHAR(500) NULL,
    compile_log TEXT NULL,
    run_log TEXT NULL,
    reevaluation TINYINT(1) NOT NULL DEFAULT 0,
    triggered_by BIGINT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_hwk_evaluation_submission (submission_id),
    KEY idx_hwk_evaluation_homework_student (homework_id, student_id),
    KEY idx_hwk_evaluation_type_status (evaluation_type, status),
    KEY idx_hwk_evaluation_started_at (started_at),
    CONSTRAINT fk_hwk_evaluation_submission
        FOREIGN KEY (submission_id) REFERENCES t_hwk_submission(id) ON DELETE CASCADE,
    CONSTRAINT fk_hwk_evaluation_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id) ON DELETE CASCADE
);


-- Source: database/migrations/20260602_01_create_lrn_learning_record.sql
CREATE TABLE IF NOT EXISTS lrn_learning_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    duration INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_record_user_started (user_id, started_at),
    KEY idx_lrn_record_user_course_started (user_id, course_id, started_at),
    KEY idx_lrn_record_rate_limit (user_id, course_id, source_module, source_id, created_at)
);


-- Source: database/migrations/20260602_02_create_hwk_review_log.sql
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


-- Source: database/migrations/20260603_01_create_lrn_notification.sql
CREATE TABLE IF NOT EXISTS lrn_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    idempotency_key VARCHAR(128) NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 1,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NULL,
    action_url VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_lrn_notification_idempotency_user (idempotency_key, user_id),
    KEY idx_lrn_notification_user_created (user_id, created_at),
    KEY idx_lrn_notification_user_type_created (user_id, type, created_at),
    KEY idx_lrn_notification_user_read_created (user_id, is_read, created_at),
    KEY idx_lrn_notification_course (course_id)
);

CREATE TABLE IF NOT EXISTS lrn_notification_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    old_status VARCHAR(32) NULL,
    new_status VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_notification_status_notification (notification_id),
    KEY idx_lrn_notification_status_user_time (user_id, operated_at),
    KEY idx_lrn_notification_status_operation (operation_type)
);


-- Source: database/migrations/20260604_01_create_lab_report.sql
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


-- Source: database/migrations/20260605_01_create_lrn_reminder_rule.sql
CREATE TABLE IF NOT EXISTS lrn_reminder_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reminder_type VARCHAR(32) NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    ahead_minutes INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_reminder_rule_user_scope (user_id, reminder_type, source_module, ahead_minutes),
    KEY idx_lrn_reminder_rule_user (user_id),
    KEY idx_lrn_reminder_rule_type_enabled (reminder_type, source_module, enabled)
);

CREATE TABLE IF NOT EXISTS lrn_notification_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    enable_experiment BOOLEAN NOT NULL DEFAULT TRUE,
    enable_homework BOOLEAN NOT NULL DEFAULT TRUE,
    enable_grade BOOLEAN NOT NULL DEFAULT TRUE,
    enable_announcement BOOLEAN NOT NULL DEFAULT TRUE,
    enable_non_critical_reminder BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_notification_setting_user (user_id)
);

CREATE TABLE IF NOT EXISTS lrn_reminder_scan_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    scan_started_at DATETIME NOT NULL,
    scan_ended_at DATETIME NULL,
    triggered_count INT NOT NULL DEFAULT 0,
    failed_reason VARCHAR(500) NULL,
    retry_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_reminder_scan_batch (batch_id),
    KEY idx_lrn_reminder_scan_started (scan_started_at),
    KEY idx_lrn_reminder_scan_retry (retry_status)
);


-- Source: database/migrations/20260605_02_create_lab_score.sql
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


-- Source: database/migrations/20260822_03_create_hwk_submission_attachment.sql
CREATE TABLE IF NOT EXISTS t_hwk_submission_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    submission_id BIGINT NULL,
    homework_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    active_slot TINYINT NULL,
    expires_at DATETIME NULL,
    bound_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    KEY idx_hwk_attachment_homework (homework_id),
    KEY idx_hwk_attachment_course (course_id),
    KEY idx_hwk_attachment_uploader (uploader_id),
    KEY idx_hwk_attachment_cleanup (status, expires_at),
    KEY idx_hwk_attachment_deleted_cleanup (status, deleted_at),
    CONSTRAINT uk_hwk_attachment_public_id UNIQUE (public_id),
    CONSTRAINT uk_hwk_attachment_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_hwk_attachment_submission UNIQUE (submission_id),
    CONSTRAINT uk_hwk_attachment_active_slot UNIQUE (homework_id, uploader_id, active_slot),
    CONSTRAINT ck_hwk_attachment_file_size CHECK (file_size > 0),
    CONSTRAINT ck_hwk_attachment_status
        CHECK (status IN ('UPLOADED', 'BOUND', 'DELETED')),
    CONSTRAINT ck_hwk_attachment_lifecycle CHECK (
        (status = 'UPLOADED'
            AND active_slot = 1
            AND submission_id IS NULL
            AND expires_at IS NOT NULL
            AND bound_at IS NULL
            AND deleted_at IS NULL)
        OR (status = 'BOUND'
            AND active_slot IS NULL
            AND submission_id IS NOT NULL
            AND expires_at IS NULL
            AND bound_at IS NOT NULL
            AND deleted_at IS NULL)
        OR (status = 'DELETED'
            AND active_slot IS NULL
            AND submission_id IS NULL
            AND expires_at IS NULL
            AND bound_at IS NULL
            AND deleted_at IS NOT NULL)
    ),
    CONSTRAINT fk_hwk_attachment_homework
        FOREIGN KEY (homework_id) REFERENCES t_hwk_homework(id) ON DELETE CASCADE,
    CONSTRAINT fk_hwk_attachment_submission
        FOREIGN KEY (submission_id) REFERENCES t_hwk_submission(id) ON DELETE CASCADE
);


-- The clean snapshot already contains every migration below. Checksums are
-- validated by database/mysql/migrate.sh before a retained database is reused.
INSERT INTO schema_migrations
    (version, checksum_sha256, installed_type, execution_ms, success)
VALUES
    ('DB-AUTH-01-auth-user-session.sql', '8fded229ef6adf7ecd09b124b0ce58f752cad240614567ad827b4b09394697af', 'COMPOSE_BASELINE', 0, 1),
    ('DB-CRS-01-course-and-member.sql', 'ab3a1b4b18b22306d6a81d056aed2ab303ed441cfc7f35a1578e2cd940a977c6', 'COMPOSE_BASELINE', 0, 1),
    ('DB-CRS-02-course-chapter.sql', '178f64f9d295d6881484d1d1b289a2d0c271802adea94684fe668ad015a620a3', 'COMPOSE_BASELINE', 0, 1),
    ('DB-CRS-03-course-resource.sql', '8f9421abb3f38241e95e5cdfea464c8e0e15e4846dda650220d45778db652e73', 'COMPOSE_BASELINE', 0, 1),
    ('DB-CRS-05-course-announcement.sql', 'b659667a1d495f5dc4b5cc193449bf3f0d6b767cb537ceafedc8bee67078772e', 'COMPOSE_BASELINE', 0, 1),
    ('20260525_01_create_grd_grade_item.sql', 'e59de71626d3338f632dc1f5df4d90cc3d4d0bd59acd412631da5713954bc2b4', 'COMPOSE_BASELINE', 0, 1),
    ('20260525_02_create_lab_experiment.sql', '880be0cba3da76ee2f17a13b00ae69451ec84d107e278f9b6b402d9b5d673bdf', 'COMPOSE_BASELINE', 0, 1),
    ('20260526_01_create_lab_submission.sql', '8afb3ac3f66c91cbd3910bc9439a5d8d8bf990564fa3545ddf80c51bd811f568', 'COMPOSE_BASELINE', 0, 1),
    ('20260530_01_create_hwk_homework.sql', 'ed38a3b54412266e75c7900b2ada5d2933b74523905beb1de8213de6331e7b44', 'COMPOSE_BASELINE', 0, 1),
    ('20260530_01_create_lrn_learning_task.sql', '8da3baa8cd80e2923ee02a4204e14edf302630d40a533393d87b16cb7fa1b04c', 'COMPOSE_BASELINE', 0, 1),
    ('20260531_01_create_lrn_learning_progress.sql', 'ec0bc51cc5d232bf6cb294aad48a1c74adb1db2a56f17bef3ac6c375b746b062', 'COMPOSE_BASELINE', 0, 1),
    ('20260601_01_create_hwk_submission.sql', 'a9b46118cd5c18c96ebcbe0b4dd2867f86772a0fb2f9f182232f9812f52ffa5c', 'COMPOSE_BASELINE', 0, 1),
    ('20260601_01_create_lab_evaluation_result.sql', '5bf10a238cce6775bd4b4537e4a18b9899ffcfad0926f68e1f484da4783633fa', 'COMPOSE_BASELINE', 0, 1),
    ('20260601_02_create_lab_evaluation.sql', 'afa54b386d1976fac72da4c6ca33db4976675abd28d9d7977189a884bcb2bf97', 'COMPOSE_BASELINE', 0, 1),
    ('20260602_01_create_hwk_evaluation.sql', '4efb7d2016d258f6de621fc6b840a3fca296d04de49bd0703a26c9bfb58c762f', 'COMPOSE_BASELINE', 0, 1),
    ('20260602_01_create_lrn_learning_record.sql', 'e52a9e11e8bb824d0c501cd5c637ddb622468aac93cae8e3733636f5ac34c7fd', 'COMPOSE_BASELINE', 0, 1),
    ('20260602_02_create_hwk_review_log.sql', '026252f17ffea580b33c18f417ede79468c45342ea42ce9662411bd4143df9a5', 'COMPOSE_BASELINE', 0, 1),
    ('20260603_01_create_lrn_notification.sql', '3ee5f11142a852abc27a64d5034dc30048329a4eaa2c746aad82f7ffa2598745', 'COMPOSE_BASELINE', 0, 1),
    ('20260604_01_create_lab_report.sql', '68a4dc9201521447c815a7436b2f6f1f680205fccafae333095ffe836335ee47', 'COMPOSE_BASELINE', 0, 1),
    ('20260605_01_create_lrn_reminder_rule.sql', '4748f9a1660682b6f008807f0bc20bba8adfd1441673aeaf8dc5d05d15fe9cff', 'COMPOSE_BASELINE', 0, 1),
    ('20260605_02_create_lab_score.sql', 'd8ec239e33a9002dd3dafb1f3153440d4821de3503eadcdf2b822af9c3ef7870', 'COMPOSE_BASELINE', 0, 1),
    ('20260606_01_add_lab_published_at.sql', '1c94a9fe22ca209ecb2b4116107026da2329be53b300294e63230d246a7a68a8', 'COMPOSE_BASELINE', 0, 1),
    ('20260822_01_add_hwk_statistics_attention_indexes.sql', '26bb671f3cc252008385a2f542b0ba6dd4dca4c040d766fba6d3e4c9582b6b22', 'COMPOSE_BASELINE', 0, 1),
    ('20260822_02_create_lab_submission_source_file.sql', 'f545c92f7b14291930dbf45dfcf48db121128b62b466ef699201e1f1de0fdb78', 'COMPOSE_BASELINE', 0, 1),
    ('20260822_03_create_hwk_submission_attachment.sql', 'c45eeca539e8a56522826833cc533789cfad169e7c6712420de1208e6b014979', 'COMPOSE_BASELINE', 0, 1),
    ('20260825_01_add_grd_analysis_source_fingerprint.sql', 'f650506d22e00f48f23da6f6dd76ca11d3f1e1083a59f805be20cf7b98315b59', 'COMPOSE_BASELINE', 0, 1),
    ('20260825_02_add_grd_analysis_source_version.sql', 'd40f5b9a41a0a836960f9b3d445e0d1f6fc6cbcc4f2b48b210387fcfc9f21b2c', 'COMPOSE_BASELINE', 0, 1);
