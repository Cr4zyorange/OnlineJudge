CREATE TABLE IF NOT EXISTS assessment_submission (
  id VARCHAR(36) PRIMARY KEY, source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL,
  course_id VARCHAR(80) NOT NULL, student_id VARCHAR(80) NOT NULL, content_ref VARCHAR(500),
  evaluation_status VARCHAR(32) NOT NULL, code_content CLOB NULL, created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS evaluation_task (
  id VARCHAR(36) PRIMARY KEY, submission_id VARCHAR(36) NOT NULL UNIQUE, source_type VARCHAR(8) NOT NULL,
  source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL, student_id VARCHAR(80) NOT NULL,
  state VARCHAR(16) NOT NULL, lease_owner VARCHAR(120), lease_until TIMESTAMP, heartbeat_at TIMESTAMP,
  attempt INTEGER NOT NULL, generation BIGINT NOT NULL, result_status VARCHAR(32), next_attempt_at TIMESTAMP,
  manual_replay_count INTEGER NOT NULL DEFAULT 0, manual_replayed_by VARCHAR(80), manual_replayed_at TIMESTAMP,
  origin_request_id VARCHAR(80) NOT NULL,
  finished_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  INDEX idx_evaluation_task_claim (state, lease_until, created_at)
);
CREATE TABLE IF NOT EXISTS assessment_event_outbox (
  event_id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(120) NOT NULL, payload_version INTEGER NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL, aggregate_id VARCHAR(160) NOT NULL, aggregate_version BIGINT NOT NULL,
  occurred_at TIMESTAMP NOT NULL, correlation_id VARCHAR(80) NOT NULL, payload_json LONGTEXT NOT NULL,
  state VARCHAR(16) NOT NULL, delivery_attempt INTEGER NOT NULL DEFAULT 0, last_error VARCHAR(1024), created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS assessment_course_member_projection (
  course_id VARCHAR(80) NOT NULL, user_id VARCHAR(80) NOT NULL, membership_status VARCHAR(16) NOT NULL,
  member_version BIGINT NOT NULL, PRIMARY KEY (course_id, user_id)
);
CREATE TABLE IF NOT EXISTS assessment_course_membership_watermark (
  course_id VARCHAR(80) PRIMARY KEY, roster_version BIGINT NOT NULL
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
CREATE TABLE IF NOT EXISTS assessment_course_member_dead_letter (
  event_id VARCHAR(80) PRIMARY KEY, payload_json LONGTEXT NOT NULL, failure_reason VARCHAR(256) NOT NULL,
  replay_count INTEGER NOT NULL DEFAULT 0, received_at TIMESTAMP NOT NULL, replayed_at TIMESTAMP NULL
);
CREATE TABLE IF NOT EXISTS assessment_identity_security_version_dead_letter (
  event_id VARCHAR(80) PRIMARY KEY, correlation_id VARCHAR(80) NOT NULL, payload_json LONGTEXT NOT NULL,
  failure_reason VARCHAR(256) NOT NULL, delivery_attempt INTEGER NOT NULL DEFAULT 1, replay_count INTEGER NOT NULL DEFAULT 0,
  received_at TIMESTAMP NOT NULL, replayed_at TIMESTAMP NULL
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
  status VARCHAR(16) NOT NULL, source_version BIGINT NOT NULL, snapshot_version BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (source_type, source_id, student_id)
);
CREATE TABLE IF NOT EXISTS assessment_source_grade_snapshot (
  source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL,
  snapshot_version BIGINT NOT NULL, first_reconstructable_version BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (source_type, source_id)
);
CREATE TABLE IF NOT EXISTS assessment_source_grade_revision (
  source_type VARCHAR(8) NOT NULL, source_id VARCHAR(80) NOT NULL, course_id VARCHAR(80) NOT NULL,
  student_id VARCHAR(80) NOT NULL, snapshot_version BIGINT NOT NULL, score DECIMAL(10,2),
  full_score DECIMAL(10,2) NOT NULL, status VARCHAR(16) NOT NULL, source_version BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (source_type, source_id, student_id, snapshot_version),
  INDEX idx_assessment_source_grade_revision_snapshot (course_id, source_type, source_id, snapshot_version, student_id)
);
CREATE TABLE IF NOT EXISTS assessment_homework (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id VARCHAR(80) NOT NULL, title VARCHAR(100) NOT NULL,
  description TEXT NOT NULL, type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, deadline TIMESTAMP NOT NULL,
  total_score DECIMAL(10,2) NOT NULL, allow_resubmit BOOLEAN NOT NULL, allow_late_submit BOOLEAN NOT NULL,
  allowed_languages VARCHAR(500) NOT NULL, created_by VARCHAR(80) NOT NULL, aggregate_version BIGINT NOT NULL,
  published_at TIMESTAMP, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_homework_course_status (course_id, status)
);
CREATE TABLE IF NOT EXISTS assessment_homework_testcase (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, homework_id BIGINT NOT NULL, input_text TEXT NOT NULL,
  expected_output TEXT NOT NULL, score_weight DECIMAL(10,2) NOT NULL, is_hidden BOOLEAN NOT NULL, sort_order INT NOT NULL,
  UNIQUE KEY uq_assessment_homework_testcase_order (homework_id, sort_order),
  CONSTRAINT fk_assessment_homework_testcase_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id)
);
CREATE TABLE IF NOT EXISTS assessment_homework_question (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, homework_id BIGINT NOT NULL, question_type VARCHAR(32) NOT NULL,
  stem TEXT NOT NULL, options_json TEXT NOT NULL, answer_json TEXT NOT NULL, score DECIMAL(10,2) NOT NULL,
  sort_order INT NOT NULL, UNIQUE KEY uq_assessment_homework_question_order (homework_id, sort_order),
  CONSTRAINT fk_assessment_homework_question_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS assessment_homework_submission (
  submission_id VARCHAR(36) PRIMARY KEY, public_id BIGINT AUTO_INCREMENT UNIQUE, homework_id BIGINT NOT NULL, student_id VARCHAR(80) NOT NULL,
  submission_version INTEGER NOT NULL, submit_type VARCHAR(20) NOT NULL DEFAULT 'CODE', language VARCHAR(40) NOT NULL, answer_text TEXT,
  answer_json TEXT, submit_status VARCHAR(20) NOT NULL, evaluation_status VARCHAR(32) NOT NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'NEED_REVIEW', auto_score DECIMAL(10,2), manual_score DECIMAL(10,2), final_score DECIMAL(10,2),
  review_comment TEXT, is_final BOOLEAN NOT NULL,
  submitted_at TIMESTAMP NOT NULL, UNIQUE KEY uq_assessment_homework_submission_version (homework_id, student_id, submission_version),
  INDEX idx_assessment_homework_submission_student (homework_id, student_id, is_final, submitted_at),
  CONSTRAINT fk_assessment_homework_submission_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id),
  CONSTRAINT fk_assessment_homework_submission_submission FOREIGN KEY (submission_id) REFERENCES assessment_submission(id)
);
CREATE TABLE IF NOT EXISTS assessment_homework_attachment (
  file_id VARCHAR(36) PRIMARY KEY, homework_id BIGINT NOT NULL, course_id VARCHAR(80) NOT NULL,
  uploader_id VARCHAR(80) NOT NULL, storage_key VARCHAR(500) NOT NULL UNIQUE, original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL, file_size BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
  expires_at TIMESTAMP NOT NULL, submission_id VARCHAR(36) NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_homework_attachment_lookup (homework_id, uploader_id, status, expires_at),
  CONSTRAINT fk_assessment_homework_attachment_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id) ON DELETE CASCADE,
  CONSTRAINT fk_assessment_homework_attachment_submission FOREIGN KEY (submission_id) REFERENCES assessment_homework_submission(submission_id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS assessment_homework_evaluation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, task_id VARCHAR(36) NOT NULL, task_generation BIGINT NOT NULL,
  submission_id VARCHAR(36) NOT NULL, homework_id BIGINT NOT NULL, student_id VARCHAR(80) NOT NULL,
  evaluation_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, score DECIMAL(10,2), full_score DECIMAL(10,2),
  started_at TIMESTAMP NOT NULL, finished_at TIMESTAMP NOT NULL,
  UNIQUE KEY uq_assessment_homework_evaluation_task_generation (task_id, task_generation),
  INDEX idx_assessment_homework_evaluation_submission (submission_id, finished_at),
  CONSTRAINT fk_assessment_homework_evaluation_task FOREIGN KEY (task_id) REFERENCES evaluation_task(id),
  CONSTRAINT fk_assessment_homework_evaluation_submission FOREIGN KEY (submission_id) REFERENCES assessment_homework_submission(submission_id),
  CONSTRAINT fk_assessment_homework_evaluation_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id)
);
CREATE TABLE IF NOT EXISTS assessment_homework_review_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, submission_id VARCHAR(36) NOT NULL, homework_id BIGINT NOT NULL,
  student_id VARCHAR(80) NOT NULL, operation_type VARCHAR(32) NOT NULL, old_score DECIMAL(10,2),
  new_score DECIMAL(10,2), operator_id VARCHAR(80) NOT NULL, reason VARCHAR(500), created_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_homework_review_log_submission (submission_id, created_at),
  CONSTRAINT fk_assessment_homework_review_log_submission FOREIGN KEY (submission_id) REFERENCES assessment_homework_submission(submission_id),
  CONSTRAINT fk_assessment_homework_review_log_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id)
);
CREATE TABLE IF NOT EXISTS assessment_lab_experiment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id VARCHAR(80) NOT NULL, title VARCHAR(100) NOT NULL,
  description CLOB NOT NULL, status VARCHAR(20) NOT NULL, deadline TIMESTAMP NOT NULL,
  max_score DECIMAL(10,2) NOT NULL, allowed_languages VARCHAR(500) NOT NULL,
  auto_evaluate BOOLEAN NOT NULL, chapter_id BIGINT NULL, attachment_ids VARCHAR(1000) NOT NULL DEFAULT '',
  evaluation_mode VARCHAR(20) NOT NULL DEFAULT 'DOCKER_IO', report_required BOOLEAN NOT NULL DEFAULT FALSE,
  time_limit_ms INT NOT NULL DEFAULT 30000, memory_limit_kb INT NOT NULL DEFAULT 262144,
  published_at TIMESTAMP NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_lab_course_status (course_id, status)
);
CREATE TABLE IF NOT EXISTS assessment_lab_submission (
  submission_id VARCHAR(36) PRIMARY KEY, lab_id BIGINT NOT NULL, student_id VARCHAR(80) NOT NULL,
  submission_version INT NOT NULL, language VARCHAR(40) NOT NULL, submit_status VARCHAR(20) NOT NULL,
  has_file BOOLEAN NOT NULL DEFAULT TRUE,
  auto_score DECIMAL(10,2), final_score DECIMAL(10,2), submitted_at TIMESTAMP NOT NULL,
  UNIQUE KEY uq_assessment_lab_submission_version (lab_id, student_id, submission_version),
  INDEX idx_assessment_lab_submission_lab_student (lab_id, student_id, submitted_at)
);
CREATE TABLE IF NOT EXISTS assessment_lab_submission_source_file (
  submission_id VARCHAR(36) PRIMARY KEY, lab_id BIGINT NOT NULL, course_id VARCHAR(80) NOT NULL,
  uploader_id VARCHAR(80) NOT NULL, storage_key VARCHAR(500) NOT NULL UNIQUE,
  original_filename VARCHAR(255) NOT NULL, content_type VARCHAR(120) NOT NULL, file_size BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  deleted_at TIMESTAMP NULL,
  INDEX idx_assessment_lab_source_file_lab (lab_id),
  INDEX idx_assessment_lab_source_file_course (course_id),
  CONSTRAINT ck_assessment_lab_source_file_size CHECK (file_size >= 0),
  CONSTRAINT ck_assessment_lab_source_file_status CHECK (status IN ('AVAILABLE', 'DELETED')),
  CONSTRAINT fk_assessment_lab_source_file_submission
    FOREIGN KEY (submission_id) REFERENCES assessment_lab_submission(submission_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS assessment_lab_testcase (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, lab_id BIGINT NOT NULL, input_text CLOB NOT NULL,
  expected_output CLOB NOT NULL, score_weight DECIMAL(10,2) NOT NULL, is_public BOOLEAN NOT NULL,
  order_num INT NOT NULL, UNIQUE KEY uq_assessment_lab_testcase_order (lab_id, order_num)
);
CREATE TABLE IF NOT EXISTS assessment_lab_evaluation_result (
  submission_id VARCHAR(36) NOT NULL, testcase_id BIGINT NOT NULL, passed BOOLEAN NOT NULL,
  score DECIMAL(10,2) NOT NULL, actual_output CLOB, message VARCHAR(500), executed_at TIMESTAMP NOT NULL,
  PRIMARY KEY (submission_id, testcase_id)
);
CREATE TABLE IF NOT EXISTS assessment_lab_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, lab_id BIGINT NOT NULL, student_id VARCHAR(80) NOT NULL,
  submission_id VARCHAR(36) NULL, storage_key VARCHAR(500) NOT NULL, file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(8) NOT NULL, content_type VARCHAR(120) NOT NULL, file_size BIGINT NOT NULL,
  report_version INT NOT NULL, score DECIMAL(10,2) NULL, comment VARCHAR(500) NULL,
  submitted_at TIMESTAMP NOT NULL, scored_by VARCHAR(80) NULL, scored_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE KEY uq_assessment_lab_report_version (lab_id, student_id, report_version),
  INDEX idx_assessment_lab_report_submission (submission_id, report_version),
  INDEX idx_assessment_lab_report_lab_student (lab_id, student_id, report_version)
);
CREATE TABLE IF NOT EXISTS assessment_lab_score (
  submission_id VARCHAR(36) PRIMARY KEY, lab_id BIGINT NOT NULL, report_id BIGINT NULL,
  auto_score DECIMAL(10,2) NULL, report_score DECIMAL(10,2) NULL, manual_score DECIMAL(10,2) NULL,
  final_score DECIMAL(10,2) NOT NULL, comment VARCHAR(500) NULL,
  scored_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_lab_score_lab (lab_id)
);
CREATE TABLE IF NOT EXISTS assessment_lab_score_change_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, submission_id VARCHAR(36) NOT NULL,
  old_final_score DECIMAL(10,2) NOT NULL, new_final_score DECIMAL(10,2) NOT NULL,
  reason VARCHAR(500) NOT NULL, operator_id VARCHAR(80) NOT NULL, created_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_lab_score_change_log_submission (submission_id),
  CONSTRAINT fk_assessment_lab_score_change_log_score FOREIGN KEY (submission_id)
    REFERENCES assessment_lab_score(submission_id) ON DELETE CASCADE
);
