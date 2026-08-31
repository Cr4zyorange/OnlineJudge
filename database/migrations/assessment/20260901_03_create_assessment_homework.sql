-- Issue #315: the HWK aggregate and submission/result facts live in Assessment.
CREATE TABLE IF NOT EXISTS assessment_homework (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id VARCHAR(80) NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT NOT NULL,
  type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  deadline TIMESTAMP NOT NULL,
  total_score DECIMAL(10,2) NOT NULL,
  allow_resubmit BOOLEAN NOT NULL,
  allow_late_submit BOOLEAN NOT NULL,
  allowed_languages VARCHAR(500) NOT NULL,
  created_by VARCHAR(80) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  published_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_homework_course_status (course_id, status)
);

CREATE TABLE IF NOT EXISTS assessment_homework_testcase (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  homework_id BIGINT NOT NULL,
  input_text TEXT NOT NULL,
  expected_output TEXT NOT NULL,
  score_weight DECIMAL(10,2) NOT NULL,
  is_hidden BOOLEAN NOT NULL,
  sort_order INT NOT NULL,
  UNIQUE KEY uq_assessment_homework_testcase_order (homework_id, sort_order),
  CONSTRAINT fk_assessment_homework_testcase_homework
    FOREIGN KEY (homework_id) REFERENCES assessment_homework(id)
);

CREATE TABLE IF NOT EXISTS assessment_homework_submission (
  submission_id VARCHAR(36) PRIMARY KEY,
  homework_id BIGINT NOT NULL,
  student_id VARCHAR(80) NOT NULL,
  submission_version INT NOT NULL,
  language VARCHAR(40) NOT NULL,
  submit_status VARCHAR(20) NOT NULL,
  evaluation_status VARCHAR(32) NOT NULL,
  auto_score DECIMAL(10,2),
  final_score DECIMAL(10,2),
  is_final BOOLEAN NOT NULL,
  submitted_at TIMESTAMP NOT NULL,
  UNIQUE KEY uq_assessment_homework_submission_version (homework_id, student_id, submission_version),
  INDEX idx_assessment_homework_submission_student (homework_id, student_id, is_final, submitted_at),
  CONSTRAINT fk_assessment_homework_submission_homework
    FOREIGN KEY (homework_id) REFERENCES assessment_homework(id),
  CONSTRAINT fk_assessment_homework_submission_submission
    FOREIGN KEY (submission_id) REFERENCES assessment_submission(id)
);
