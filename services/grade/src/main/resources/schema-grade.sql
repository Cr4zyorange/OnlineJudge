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
