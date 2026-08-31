-- #306: LRN is Course-owned; this migration intentionally has no separate schema.
CREATE TABLE IF NOT EXISTS lrn_learning_task (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, course_id BIGINT NOT NULL,
  source_module VARCHAR(32) NOT NULL, source_id VARCHAR(128) NOT NULL, title VARCHAR(200) NOT NULL,
  deadline DATETIME NULL, status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_course_lrn_task (user_id, source_module, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lrn_notification (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, course_id BIGINT NULL,
  source_module VARCHAR(32) NOT NULL, source_id VARCHAR(128) NOT NULL, title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL, is_read TINYINT(1) NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_course_lrn_notice (user_id, source_module, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
