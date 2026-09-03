-- Preserve OBJECTIVE homework authoring facts inside the Assessment aggregate.
CREATE TABLE assessment_homework_question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  homework_id BIGINT NOT NULL,
  question_type VARCHAR(32) NOT NULL,
  stem TEXT NOT NULL,
  options_json TEXT NOT NULL,
  answer_json TEXT NOT NULL,
  score DECIMAL(10,2) NOT NULL,
  sort_order INT NOT NULL,
  UNIQUE KEY uq_assessment_homework_question_order (homework_id, sort_order),
  CONSTRAINT fk_assessment_homework_question_homework
    FOREIGN KEY (homework_id) REFERENCES assessment_homework(id) ON DELETE CASCADE
);
