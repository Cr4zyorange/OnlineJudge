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
