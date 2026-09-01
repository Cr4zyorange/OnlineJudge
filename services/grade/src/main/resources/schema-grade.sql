ALTER TABLE t_grade_analysis_snapshot ADD COLUMN IF NOT EXISTS total_student_count INT NULL;
ALTER TABLE t_grade_analysis_snapshot ADD COLUMN IF NOT EXISTS completed_count INT NULL;
ALTER TABLE t_grade_analysis_snapshot ADD COLUMN IF NOT EXISTS missing_count INT NULL;
ALTER TABLE t_grade_analysis_snapshot ADD COLUMN IF NOT EXISTS unsubmitted_count INT NULL;
ALTER TABLE t_grade_analysis_snapshot ADD COLUMN IF NOT EXISTS ungraded_count INT NULL;
ALTER TABLE t_grade_analysis_snapshot ADD COLUMN IF NOT EXISTS source_fingerprint VARCHAR(96) NULL;

CREATE TABLE IF NOT EXISTS grade_rule_version (
    grade_item_id BIGINT PRIMARY KEY,
    rule_version BIGINT NOT NULL,
    rule_fingerprint VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS grade_result_trace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    calculation_batch_id BIGINT NOT NULL,
    grade_record_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    grade_item_id BIGINT NOT NULL,
    source_version BIGINT NULL,
    rule_version BIGINT NOT NULL,
    grade_status VARCHAR(30) NOT NULL,
    raw_score DECIMAL(12, 4) NULL,
    weighted_score DECIMAL(12, 4) NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_grade_result_trace_batch_record UNIQUE (calculation_batch_id, grade_record_id)
);
CREATE INDEX IF NOT EXISTS idx_grade_result_trace_lookup
    ON grade_result_trace (course_id, student_id, grade_item_id, calculation_batch_id);

CREATE TABLE IF NOT EXISTS t_grade_analysis_source_version (
    course_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    grade_item_key BIGINT NOT NULL,
    source_version BIGINT NOT NULL,
    source_data_time TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (course_id, target_type, grade_item_key)
);
CREATE INDEX IF NOT EXISTS idx_grade_analysis_source_version_updated
    ON t_grade_analysis_source_version (updated_at);

CREATE TABLE IF NOT EXISTS grade_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(160) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_grade_event_inbox_consumer_event UNIQUE (consumer_name, event_id)
);
CREATE INDEX IF NOT EXISTS idx_grade_event_inbox_aggregate
    ON grade_event_inbox (consumer_name, aggregate_type, aggregate_id, aggregate_version);

CREATE TABLE IF NOT EXISTS grade_event_outbox (
    event_id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(160) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    delivery_attempt INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP NULL,
    last_error VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_grade_event_outbox_business UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_grade_event_outbox_due
    ON grade_event_outbox (delivery_status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS grade_source_projection_watermark (
    aggregate_id VARCHAR(160) PRIMARY KEY,
    current_version BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS grade_source_projection (
    aggregate_id VARCHAR(160) PRIMARY KEY,
    course_id VARCHAR(80) NOT NULL,
    source_type VARCHAR(8) NOT NULL,
    source_id VARCHAR(80) NOT NULL,
    student_id VARCHAR(80) NOT NULL,
    score DECIMAL(12, 4) NULL,
    full_score DECIMAL(12, 4) NOT NULL,
    source_status VARCHAR(16) NOT NULL,
    source_version BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_grade_source_projection_source UNIQUE (source_type, source_id, student_id),
    CONSTRAINT ck_grade_source_projection_full_score CHECK (full_score > 0),
    CONSTRAINT ck_grade_source_projection_status CHECK (source_status IN ('SCORED', 'UNGRADED')),
    CONSTRAINT ck_grade_source_projection_score CHECK (
        (source_status = 'SCORED' AND score IS NOT NULL AND score >= 0 AND score <= full_score)
        OR (source_status = 'UNGRADED' AND score IS NULL)
    )
);
CREATE INDEX IF NOT EXISTS idx_grade_source_projection_course
    ON grade_source_projection (course_id, source_type, source_id);

CREATE TABLE IF NOT EXISTS grade_source_deferred_event (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_id VARCHAR(160) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    course_id VARCHAR(80) NOT NULL,
    source_type VARCHAR(8) NOT NULL,
    source_id VARCHAR(80) NOT NULL,
    student_id VARCHAR(80) NOT NULL,
    score DECIMAL(12, 4) NULL,
    full_score DECIMAL(12, 4) NOT NULL,
    source_status VARCHAR(16) NOT NULL,
    source_version BIGINT NOT NULL,
    CONSTRAINT uq_grade_source_deferred_version UNIQUE (aggregate_id, source_version)
);

CREATE TABLE IF NOT EXISTS grade_source_projection_gap (
    aggregate_id VARCHAR(160) PRIMARY KEY,
    expected_version BIGINT NOT NULL,
    observed_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS grade_source_reconciliation_request (
    aggregate_id VARCHAR(160) PRIMARY KEY,
    expected_version BIGINT NOT NULL,
    observed_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    request_status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT ck_grade_source_reconciliation_status CHECK (request_status IN ('PENDING', 'RESOLVED'))
);
