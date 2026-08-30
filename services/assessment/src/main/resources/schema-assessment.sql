CREATE TABLE IF NOT EXISTS assessment_submission (
  id VARCHAR(36) PRIMARY KEY, source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL,
  course_id VARCHAR(80) NOT NULL, student_id VARCHAR(80) NOT NULL, content_ref VARCHAR(500),
  evaluation_status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS evaluation_task (
  id VARCHAR(36) PRIMARY KEY, submission_id VARCHAR(36) NOT NULL UNIQUE, source_type VARCHAR(8) NOT NULL,
  source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL, student_id VARCHAR(80) NOT NULL,
  state VARCHAR(16) NOT NULL, lease_owner VARCHAR(120), lease_until TIMESTAMP, heartbeat_at TIMESTAMP,
  attempt INTEGER NOT NULL, generation BIGINT NOT NULL, result_status VARCHAR(32), finished_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  INDEX idx_evaluation_task_claim (state, lease_until, created_at)
);
CREATE TABLE IF NOT EXISTS assessment_event_outbox (
  event_id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(120) NOT NULL, payload_version INTEGER NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL, aggregate_id VARCHAR(160) NOT NULL, aggregate_version BIGINT NOT NULL,
  occurred_at TIMESTAMP NOT NULL, correlation_id VARCHAR(80) NOT NULL, payload_json LONGTEXT NOT NULL,
  state VARCHAR(16) NOT NULL, created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_course_member_projection (
  course_id VARCHAR(80) NOT NULL, user_id VARCHAR(80) NOT NULL, membership_status VARCHAR(16) NOT NULL,
  member_version BIGINT NOT NULL, PRIMARY KEY (course_id, user_id)
);
CREATE TABLE IF NOT EXISTS assessment_event_inbox (event_id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(120) NOT NULL);
CREATE TABLE IF NOT EXISTS assessment_course_projection_gap (
  course_id VARCHAR(80) NOT NULL, user_id VARCHAR(80) NOT NULL, expected_version BIGINT NOT NULL,
  observed_version BIGINT NOT NULL, PRIMARY KEY (course_id, user_id)
);
CREATE TABLE IF NOT EXISTS assessment_deferred_course_member_event (
  event_id VARCHAR(36) PRIMARY KEY, course_id VARCHAR(80) NOT NULL, user_id VARCHAR(80) NOT NULL,
  membership_status VARCHAR(16) NOT NULL, member_version BIGINT NOT NULL,
  UNIQUE KEY uq_assessment_deferred_member_version (course_id, user_id, member_version)
);
CREATE TABLE IF NOT EXISTS assessment_source_grade (
  source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL,
  student_id VARCHAR(80) NOT NULL, score DECIMAL(10,2), full_score DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL, source_version BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (source_type, source_id, student_id)
);
