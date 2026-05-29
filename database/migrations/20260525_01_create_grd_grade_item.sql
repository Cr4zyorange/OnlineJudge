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

CREATE INDEX IF NOT EXISTS idx_grade_item_course
    ON t_grade_item (course_id, enabled, deleted, sort_order);

CREATE INDEX IF NOT EXISTS idx_grade_item_source
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

CREATE INDEX IF NOT EXISTS idx_grade_record_course_status
    ON t_grade_record (course_id, grade_status, publish_status);

CREATE INDEX IF NOT EXISTS idx_grade_record_student_publish
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

CREATE INDEX IF NOT EXISTS idx_course_grade_publish
    ON t_course_grade_summary (course_id, publish_status);

CREATE TABLE IF NOT EXISTS t_grade_publish_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    publish_scope VARCHAR(30) NOT NULL,
    published_count INT NOT NULL DEFAULT 0,
    published_by BIGINT NOT NULL,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notification_status VARCHAR(30) NOT NULL,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_grade_publish_record_course
    ON t_grade_publish_record (course_id, published_at);

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

CREATE INDEX IF NOT EXISTS idx_grade_calculation_batch_course
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

CREATE INDEX IF NOT EXISTS idx_grade_change_log_course
    ON t_grade_change_log (course_id, student_id, grade_item_id, created_at);
