-- #339 forward-only completion of the Grade-owned schema. V01 is retained for checksum compatibility.

CREATE TABLE IF NOT EXISTS t_grade_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    CONSTRAINT ck_grade_item_full_score CHECK (full_score > 0),
    CONSTRAINT ck_grade_item_weight CHECK (weight >= 0 AND weight <= 1),
    INDEX idx_grade_item_course (course_id, enabled, deleted, sort_order),
    INDEX idx_grade_item_source (source_type, source_id)
);

CREATE TABLE IF NOT EXISTS t_grade_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    CONSTRAINT uk_grade_record_student_item UNIQUE (course_id, student_id, grade_item_id),
    INDEX idx_grade_record_course_status (course_id, grade_status, publish_status),
    INDEX idx_grade_record_student_publish (course_id, student_id, publish_status)
);

CREATE TABLE IF NOT EXISTS t_course_grade_summary (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    final_score DECIMAL(6,2) NULL,
    final_status VARCHAR(30) NOT NULL,
    publish_status VARCHAR(30) NOT NULL DEFAULT 'UNPUBLISHED',
    calculation_batch_id BIGINT NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_course_grade_student UNIQUE (course_id, student_id),
    INDEX idx_course_grade_publish (course_id, publish_status)
);

CREATE TABLE IF NOT EXISTS t_grade_publish_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    publish_scope VARCHAR(30) NOT NULL,
    published_count INT NOT NULL DEFAULT 0,
    published_by BIGINT NOT NULL,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notification_status VARCHAR(30) NOT NULL,
    remark VARCHAR(500) NULL,
    CONSTRAINT uk_grade_publish_record_idempotency UNIQUE (course_id, idempotency_key),
    INDEX idx_grade_publish_record_course (course_id, published_at)
);

CREATE TABLE IF NOT EXISTS t_grade_calculation_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    affected_item_count INT NOT NULL DEFAULT 0,
    affected_student_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    message VARCHAR(1000) NULL,
    calculated_by BIGINT NOT NULL,
    calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_grade_calculation_batch_course (course_id, calculated_at)
);

CREATE TABLE IF NOT EXISTS t_grade_change_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    grade_item_id BIGINT NULL,
    change_type VARCHAR(30) NOT NULL,
    old_value DECIMAL(6,2) NULL,
    new_value DECIMAL(6,2) NULL,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_grade_change_log_course (course_id, student_id, grade_item_id, created_at)
);

CREATE TABLE IF NOT EXISTS t_grade_review_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    INDEX idx_grade_review_course_status (course_id, status),
    INDEX idx_grade_review_student_status (course_id, student_id, status),
    INDEX idx_grade_review_target_pending (course_id, student_id, target_type, grade_item_id, status)
);

CREATE TABLE IF NOT EXISTS t_grade_analysis_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    INDEX idx_grade_analysis_snapshot_course (course_id, target_type, grade_item_id, generated_at)
);

CREATE TABLE IF NOT EXISTS t_grade_analysis_source_version (
    course_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    grade_item_key BIGINT NOT NULL,
    source_version BIGINT NOT NULL,
    source_data_time DATETIME NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (course_id, target_type, grade_item_key),
    INDEX idx_grade_analysis_source_version_updated (updated_at)
);

ALTER TABLE grade_source_projection
    ADD COLUMN aggregate_id VARCHAR(160) NULL,
    ADD COLUMN source_status VARCHAR(16) NULL,
    ADD COLUMN occurred_at DATETIME NULL;
UPDATE grade_source_projection
   SET aggregate_id=CONCAT(source_type, ':', source_id, ':', student_id),
       source_status=status,
       occurred_at=updated_at
 WHERE aggregate_id IS NULL OR source_status IS NULL OR occurred_at IS NULL;
ALTER TABLE grade_source_projection
    MODIFY aggregate_id VARCHAR(160) NOT NULL,
    MODIFY source_status VARCHAR(16) NOT NULL,
    MODIFY occurred_at DATETIME NOT NULL,
    MODIFY score DECIMAL(12,4) NULL,
    MODIFY full_score DECIMAL(12,4) NOT NULL;
CREATE UNIQUE INDEX uq_grade_source_projection_aggregate ON grade_source_projection (aggregate_id);
CREATE INDEX idx_grade_source_projection_course ON grade_source_projection (course_id, source_type, source_id);

ALTER TABLE grade_event_inbox
    ADD COLUMN consumer_name VARCHAR(64) NOT NULL DEFAULT 'grade-source-projection',
    ADD COLUMN aggregate_type VARCHAR(64) NULL,
    ADD COLUMN aggregate_id VARCHAR(160) NULL,
    ADD COLUMN aggregate_version BIGINT NULL,
    ADD COLUMN correlation_id VARCHAR(64) NULL,
    ADD COLUMN processing_status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    ADD COLUMN processed_at DATETIME NULL;
UPDATE grade_event_inbox
   SET aggregate_type=COALESCE(aggregate_type, 'assessment-source-grade'),
       aggregate_id=COALESCE(aggregate_id, event_id),
       aggregate_version=COALESCE(aggregate_version, 1),
       correlation_id=COALESCE(correlation_id, event_id),
       processed_at=COALESCE(processed_at, received_at);
ALTER TABLE grade_event_inbox
    MODIFY aggregate_type VARCHAR(64) NOT NULL,
    MODIFY aggregate_id VARCHAR(160) NOT NULL,
    MODIFY aggregate_version BIGINT NOT NULL,
    MODIFY correlation_id VARCHAR(64) NOT NULL,
    MODIFY processed_at DATETIME NOT NULL;
CREATE INDEX idx_grade_event_inbox_aggregate ON grade_event_inbox
    (consumer_name, aggregate_type, aggregate_id, aggregate_version);

CREATE TABLE IF NOT EXISTS grade_event_outbox (
    event_id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(160) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    delivery_attempt INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    delivered_at DATETIME NULL,
    last_error VARCHAR(1024) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_grade_event_outbox_business UNIQUE (idempotency_key),
    INDEX idx_grade_event_outbox_due (delivery_status, next_attempt_at, created_at)
);

CREATE TABLE IF NOT EXISTS grade_source_projection_watermark (
    aggregate_id VARCHAR(160) PRIMARY KEY,
    current_version BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS grade_source_deferred_event (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_id VARCHAR(160) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    course_id VARCHAR(80) NOT NULL,
    source_type VARCHAR(8) NOT NULL,
    source_id VARCHAR(80) NOT NULL,
    student_id VARCHAR(80) NOT NULL,
    score DECIMAL(12,4) NULL,
    full_score DECIMAL(12,4) NOT NULL,
    source_status VARCHAR(16) NOT NULL,
    source_version BIGINT NOT NULL,
    CONSTRAINT uq_grade_source_deferred_version UNIQUE (aggregate_id, source_version)
);

CREATE TABLE IF NOT EXISTS grade_source_projection_gap (
    aggregate_id VARCHAR(160) PRIMARY KEY,
    expected_version BIGINT NOT NULL,
    observed_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS grade_source_reconciliation_request (
    aggregate_id VARCHAR(160) PRIMARY KEY,
    expected_version BIGINT NOT NULL,
    observed_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    request_status VARCHAR(16) NOT NULL,
    requested_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    CONSTRAINT ck_grade_source_reconciliation_status CHECK (request_status IN ('PENDING','RESOLVED'))
);

CREATE TABLE IF NOT EXISTS grade_rule_version (
    grade_item_id BIGINT PRIMARY KEY,
    rule_version BIGINT NOT NULL,
    rule_fingerprint VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS grade_result_trace (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    calculation_batch_id BIGINT NOT NULL,
    grade_record_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    grade_item_id BIGINT NOT NULL,
    source_version BIGINT NULL,
    rule_version BIGINT NOT NULL,
    grade_status VARCHAR(30) NOT NULL,
    raw_score DECIMAL(12,4) NULL,
    weighted_score DECIMAL(12,4) NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_grade_result_trace_batch_record UNIQUE (calculation_batch_id, grade_record_id),
    INDEX idx_grade_result_trace_lookup (course_id, student_id, grade_item_id, calculation_batch_id)
);
