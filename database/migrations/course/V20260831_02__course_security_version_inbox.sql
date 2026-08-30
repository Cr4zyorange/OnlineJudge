-- #312 Course consumes Identity security-version facts locally.  This is
-- Course-owned runtime state, not a shared Identity session table.
CREATE TABLE IF NOT EXISTS event_inbox (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_course_event_inbox_identity (event_type, aggregate_id, aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
