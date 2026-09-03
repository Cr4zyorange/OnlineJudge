-- Issue #320: Assessment owns the trusted, submission-version-scoped LAB
-- source-file asset.  Public APIs expose only safe metadata; storage_key stays
-- server-side and is re-authorized for every teacher download.
CREATE TABLE IF NOT EXISTS assessment_lab_submission_source_file (
  submission_id VARCHAR(36) PRIMARY KEY,
  lab_id BIGINT NOT NULL,
  course_id VARCHAR(80) NOT NULL,
  uploader_id VARCHAR(80) NOT NULL,
  storage_key VARCHAR(500) NOT NULL UNIQUE,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  deleted_at TIMESTAMP NULL,
  INDEX idx_assessment_lab_source_file_lab (lab_id),
  INDEX idx_assessment_lab_source_file_course (course_id),
  CONSTRAINT ck_assessment_lab_source_file_size CHECK (file_size >= 0),
  CONSTRAINT ck_assessment_lab_source_file_status CHECK (status IN ('AVAILABLE', 'DELETED')),
  CONSTRAINT fk_assessment_lab_source_file_submission
    FOREIGN KEY (submission_id) REFERENCES assessment_lab_submission(submission_id) ON DELETE CASCADE
);
