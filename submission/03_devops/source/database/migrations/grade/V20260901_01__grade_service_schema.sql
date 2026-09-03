CREATE TABLE IF NOT EXISTS grade_source_projection (
  source_type VARCHAR(32) NOT NULL, source_id VARCHAR(128) NOT NULL, student_id VARCHAR(128) NOT NULL,
  course_id VARCHAR(128) NOT NULL, score DECIMAL(10,2) NULL, full_score DECIMAL(10,2) NOT NULL,
  status VARCHAR(32) NOT NULL, source_version BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (source_type, source_id, student_id)
);
CREATE TABLE IF NOT EXISTS grade_event_inbox (
  event_id VARCHAR(64) NOT NULL PRIMARY KEY, event_type VARCHAR(128) NOT NULL, received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
