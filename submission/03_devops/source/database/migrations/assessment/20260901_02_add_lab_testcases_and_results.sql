-- Issue #314: testcase configuration and durable, submission-version-scoped results.
CREATE TABLE IF NOT EXISTS assessment_lab_testcase (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lab_id BIGINT NOT NULL,
  input_text TEXT NOT NULL,
  expected_output TEXT NOT NULL,
  score_weight DECIMAL(10,2) NOT NULL,
  is_public BOOLEAN NOT NULL,
  order_num INT NOT NULL,
  UNIQUE KEY uq_assessment_lab_testcase_order (lab_id, order_num)
);

CREATE TABLE IF NOT EXISTS assessment_lab_evaluation_result (
  submission_id VARCHAR(36) NOT NULL,
  testcase_id BIGINT NOT NULL,
  passed BOOLEAN NOT NULL,
  score DECIMAL(10,2) NOT NULL,
  actual_output TEXT,
  message VARCHAR(500),
  executed_at TIMESTAMP NOT NULL,
  PRIMARY KEY (submission_id, testcase_id)
);
