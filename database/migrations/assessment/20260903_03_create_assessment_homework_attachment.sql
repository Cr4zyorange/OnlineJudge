-- Issue #320: stage student-owned FILE uploads before atomically binding one to a submission.
CREATE TABLE assessment_homework_attachment (
  file_id VARCHAR(36) PRIMARY KEY,
  homework_id BIGINT NOT NULL,
  course_id VARCHAR(80) NOT NULL,
  uploader_id VARCHAR(80) NOT NULL,
  storage_key VARCHAR(500) NOT NULL UNIQUE,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  submission_id VARCHAR(36) NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_homework_attachment_lookup (homework_id, uploader_id, status, expires_at),
  CONSTRAINT fk_assessment_homework_attachment_homework
    FOREIGN KEY (homework_id) REFERENCES assessment_homework(id) ON DELETE CASCADE,
  CONSTRAINT fk_assessment_homework_attachment_submission
    FOREIGN KEY (submission_id) REFERENCES assessment_homework_submission(submission_id) ON DELETE SET NULL
);
