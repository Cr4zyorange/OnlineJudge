-- The Course v2 Identity-security projection is independent of the Course
-- business-table evolution.  Keeping it separate lets the pre-existing H2
-- contract suite exercise the production V20260831_02 inbox migration
-- behavior while the DB-CRS schema evolves in a later Course migration.
CREATE TABLE IF NOT EXISTS event_inbox (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_course_event_inbox_identity (event_type, aggregate_id, aggregate_version)
);
