-- Keep the browser-facing numeric submission key separate from the Assessment
-- worker UUID, and retain every terminal execution plus explicit REJUDGE audit.
ALTER TABLE assessment_homework_submission
  ADD COLUMN public_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE AFTER submission_id;

CREATE TABLE assessment_homework_evaluation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id VARCHAR(36) NOT NULL,
  task_generation BIGINT NOT NULL,
  submission_id VARCHAR(36) NOT NULL,
  homework_id BIGINT NOT NULL,
  student_id VARCHAR(80) NOT NULL,
  evaluation_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  score DECIMAL(10,2),
  full_score DECIMAL(10,2),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP NOT NULL,
  UNIQUE KEY uq_assessment_homework_evaluation_task_generation (task_id, task_generation),
  INDEX idx_assessment_homework_evaluation_submission (submission_id, finished_at),
  CONSTRAINT fk_assessment_homework_evaluation_task FOREIGN KEY (task_id) REFERENCES evaluation_task(id),
  CONSTRAINT fk_assessment_homework_evaluation_submission FOREIGN KEY (submission_id) REFERENCES assessment_homework_submission(submission_id),
  CONSTRAINT fk_assessment_homework_evaluation_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id)
);

CREATE TABLE assessment_homework_review_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  submission_id VARCHAR(36) NOT NULL,
  homework_id BIGINT NOT NULL,
  student_id VARCHAR(80) NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  old_score DECIMAL(10,2),
  new_score DECIMAL(10,2),
  operator_id VARCHAR(80) NOT NULL,
  reason VARCHAR(500),
  created_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_homework_review_log_submission (submission_id, created_at),
  CONSTRAINT fk_assessment_homework_review_log_submission FOREIGN KEY (submission_id) REFERENCES assessment_homework_submission(submission_id),
  CONSTRAINT fk_assessment_homework_review_log_homework FOREIGN KEY (homework_id) REFERENCES assessment_homework(id)
);
