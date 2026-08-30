-- #337: every table below has one service owner.  It is intentionally not a
-- shared cross-service ledger: the service account for each schema gets access
-- only to its own outbox/inbox/DLQ/projection tables after #309/#341 cutover.

CREATE TABLE IF NOT EXISTS assessment_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    routing_key VARCHAR(192) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP NULL,
    last_error VARCHAR(1024) NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assessment_event_outbox_event UNIQUE (event_id),
    KEY idx_assessment_event_outbox_due (delivery_status, next_attempt_at, lease_until),
    KEY idx_assessment_event_outbox_correlation (correlation_id)
);

CREATE TABLE IF NOT EXISTS course_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    routing_key VARCHAR(192) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP NULL,
    last_error VARCHAR(1024) NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_course_event_outbox_event UNIQUE (event_id),
    KEY idx_course_event_outbox_due (delivery_status, next_attempt_at, lease_until),
    KEY idx_course_event_outbox_correlation (correlation_id)
);
CREATE TABLE IF NOT EXISTS grade_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    routing_key VARCHAR(192) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP NULL,
    last_error VARCHAR(1024) NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_grade_event_outbox_event UNIQUE (event_id),
    KEY idx_grade_event_outbox_due (delivery_status, next_attempt_at, lease_until),
    KEY idx_grade_event_outbox_correlation (correlation_id)
);

CREATE TABLE IF NOT EXISTS assessment_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assessment_event_inbox_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_assessment_event_inbox_aggregate (consumer_name, aggregate_type, aggregate_id, aggregate_version)
);

CREATE TABLE IF NOT EXISTS grade_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_grade_event_inbox_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_grade_event_inbox_aggregate (consumer_name, aggregate_type, aggregate_id, aggregate_version)
);
CREATE TABLE IF NOT EXISTS learning_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learning_event_inbox_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_learning_event_inbox_aggregate (consumer_name, aggregate_type, aggregate_id, aggregate_version)
);

CREATE TABLE IF NOT EXISTS learning_event_delivery_attempt (
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    attempt_count INT NOT NULL,
    last_error VARCHAR(1024) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS learning_event_dead_letter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    envelope_json TEXT NOT NULL,
    failure_classification VARCHAR(64) NOT NULL,
    failure_message VARCHAR(1024) NOT NULL,
    attempt_count INT NOT NULL,
    replayed_at TIMESTAMP NULL,
    replayed_by VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learning_dead_letter_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_learning_dead_letter_created (created_at),
    KEY idx_learning_dead_letter_correlation (correlation_id)
);

CREATE TABLE IF NOT EXISTS learning_event_reconciliation_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    observed_version BIGINT NOT NULL,
    last_applied_version BIGINT NOT NULL,
    triggering_event_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    request_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT uq_learning_reconciliation_gap UNIQUE (consumer_name, aggregate_type, aggregate_id, observed_version),
    KEY idx_learning_reconciliation_open (request_status, created_at)
);

CREATE TABLE IF NOT EXISTS learning_deferred_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    envelope_json TEXT NOT NULL,
    deferral_reason VARCHAR(64) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP NULL,
    last_error VARCHAR(1024) NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learning_deferred_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_learning_deferred_due (delivery_status, next_attempt_at, lease_until),
    KEY idx_learning_deferred_correlation (correlation_id)
);

CREATE TABLE IF NOT EXISTS learning_course_member_projection (
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    membership_status VARCHAR(32) NOT NULL,
    member_version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (course_id, user_id),
    KEY idx_learning_course_member_active (course_id, membership_status)
);
