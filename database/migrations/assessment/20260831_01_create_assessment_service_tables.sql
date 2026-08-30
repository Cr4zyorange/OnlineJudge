-- #313 owns this schema-local queue/projection/outbox topology.  #341 applies the five-schema
-- control-plane migration; this script is the Assessment service's repeatable local schema input.
CREATE TABLE IF NOT EXISTS assessment_submission (
  id VARCHAR(36) PRIMARY KEY, source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL,
  course_id VARCHAR(80) NOT NULL, student_id VARCHAR(80) NOT NULL, content_ref VARCHAR(500),
  evaluation_status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS evaluation_task (
  id VARCHAR(36) PRIMARY KEY, submission_id VARCHAR(36) NOT NULL UNIQUE, source_type VARCHAR(8) NOT NULL,
  source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL, student_id VARCHAR(80) NOT NULL,
  state VARCHAR(16) NOT NULL, lease_owner VARCHAR(120), lease_until TIMESTAMP NULL, heartbeat_at TIMESTAMP NULL,
  attempt INT NOT NULL, generation BIGINT NOT NULL, result_status VARCHAR(32), next_attempt_at TIMESTAMP NULL,
  manual_replay_count INT NOT NULL DEFAULT 0, manual_replayed_by VARCHAR(80), manual_replayed_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, INDEX idx_evaluation_task_claim (state, lease_until, created_at)
);
CREATE TABLE IF NOT EXISTS assessment_event_outbox (
  event_id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(120) NOT NULL, payload_version INT NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL, aggregate_id VARCHAR(160) NOT NULL, aggregate_version BIGINT NOT NULL,
  occurred_at TIMESTAMP NOT NULL, correlation_id VARCHAR(80) NOT NULL, payload_json LONGTEXT NOT NULL,
  state VARCHAR(16) NOT NULL, delivery_attempt INT NOT NULL DEFAULT 0, last_error VARCHAR(1024), created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_course_member_projection (
  course_id VARCHAR(80) NOT NULL, user_id VARCHAR(80) NOT NULL, membership_status VARCHAR(16) NOT NULL,
  member_version BIGINT NOT NULL, PRIMARY KEY (course_id, user_id)
);
CREATE TABLE IF NOT EXISTS assessment_course_membership_watermark (
  course_id VARCHAR(80) PRIMARY KEY, roster_version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_event_inbox (
  event_id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(120) NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_course_projection_gap (
  course_id VARCHAR(80) NOT NULL, user_id VARCHAR(80) NOT NULL, expected_version BIGINT NOT NULL,
  observed_version BIGINT NOT NULL, PRIMARY KEY (course_id, user_id)
);
CREATE TABLE IF NOT EXISTS assessment_deferred_course_member_event (
  event_id VARCHAR(36) PRIMARY KEY, course_id VARCHAR(80) NOT NULL, user_id VARCHAR(80) NOT NULL,
  membership_status VARCHAR(16) NOT NULL, member_version BIGINT NOT NULL,
  UNIQUE KEY uq_assessment_deferred_member_version (course_id, user_id, member_version)
);
CREATE TABLE IF NOT EXISTS assessment_course_member_dead_letter (
  event_id VARCHAR(80) PRIMARY KEY, payload_json LONGTEXT NOT NULL, failure_reason VARCHAR(256) NOT NULL,
  replay_count INT NOT NULL DEFAULT 0, received_at TIMESTAMP NOT NULL, replayed_at TIMESTAMP NULL
);
CREATE TABLE IF NOT EXISTS assessment_identity_security_version (
  user_id VARCHAR(80) PRIMARY KEY, minimum_security_version BIGINT NOT NULL,
  aggregate_version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_identity_security_version_event_inbox (
  event_id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(80) NOT NULL, aggregate_version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_identity_security_version_gap (
  user_id VARCHAR(80) PRIMARY KEY, expected_version BIGINT NOT NULL, observed_version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_deferred_identity_security_version_event (
  event_id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(80) NOT NULL, security_version BIGINT NOT NULL,
  change_reason VARCHAR(32) NOT NULL, aggregate_version BIGINT NOT NULL,
  UNIQUE KEY uq_assessment_deferred_security_version (user_id, aggregate_version)
);
CREATE TABLE IF NOT EXISTS assessment_source_grade (
  source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL,
  student_id VARCHAR(80) NOT NULL, score DECIMAL(10,2), full_score DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL, source_version BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (source_type, source_id, student_id)
);
CREATE TABLE IF NOT EXISTS assessment_source_grade_snapshot (
  source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL,
  snapshot_version BIGINT NOT NULL,
  PRIMARY KEY (source_type, source_id)
);
