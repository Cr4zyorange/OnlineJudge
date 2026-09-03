-- Issue #320: persist the versioned LAB report contract before API/worker
-- runtime begins reading it.  The migration job owns this DDL; Compose runtime
-- accounts are intentionally DML-only and cannot self-heal a missing table.
CREATE TABLE IF NOT EXISTS assessment_lab_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lab_id BIGINT NOT NULL,
  student_id VARCHAR(80) NOT NULL,
  submission_id VARCHAR(36) NULL,
  storage_key VARCHAR(500) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(8) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL,
  report_version INT NOT NULL,
  score DECIMAL(10,2) NULL,
  comment VARCHAR(500) NULL,
  submitted_at TIMESTAMP NOT NULL,
  scored_by VARCHAR(80) NULL,
  scored_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE KEY uq_assessment_lab_report_version (lab_id, student_id, report_version),
  INDEX idx_assessment_lab_report_submission (submission_id, report_version),
  INDEX idx_assessment_lab_report_lab_student (lab_id, student_id, report_version)
);
