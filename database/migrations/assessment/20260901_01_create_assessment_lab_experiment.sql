-- Issue #314: LAB owns its experiment aggregate inside the Assessment schema.
-- The generic assessment_submission/evaluation_task pair remains the shared
-- execution core; it is intentionally not used as a substitute for a Lab.
CREATE TABLE IF NOT EXISTS assessment_lab_experiment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id VARCHAR(80) NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  deadline TIMESTAMP NOT NULL,
  max_score DECIMAL(10,2) NOT NULL,
  allowed_languages VARCHAR(500) NOT NULL,
  auto_evaluate BOOLEAN NOT NULL,
  created_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_lab_course_status (course_id, status)
);

CREATE TABLE IF NOT EXISTS assessment_lab_submission (
  submission_id VARCHAR(36) PRIMARY KEY,
  lab_id BIGINT NOT NULL,
  student_id VARCHAR(80) NOT NULL,
  submission_version INT NOT NULL,
  language VARCHAR(40) NOT NULL,
  submit_status VARCHAR(20) NOT NULL,
  auto_score DECIMAL(10,2),
  final_score DECIMAL(10,2),
  submitted_at TIMESTAMP NOT NULL,
  UNIQUE KEY uq_assessment_lab_submission_version (lab_id, student_id, submission_version),
  INDEX idx_assessment_lab_submission_lab_student (lab_id, student_id, submitted_at)
);
