CREATE TABLE IF NOT EXISTS lrn_learning_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    last_position VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_progress_user_course_source (user_id, course_id, source_module, source_id),
    KEY idx_lrn_progress_user_course_chapter (user_id, course_id, chapter_id),
    KEY idx_lrn_progress_updated_at (updated_at)
);
