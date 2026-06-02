CREATE TABLE IF NOT EXISTS crs_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_top TINYINT(1) NOT NULL DEFAULT 0,
    publisher_id BIGINT NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_announcement_course_top_time (course_id, is_top, created_at),
    INDEX idx_crs_announcement_publisher (publisher_id),
    CONSTRAINT fk_crs_announcement_course FOREIGN KEY (course_id) REFERENCES crs_course (id)
);
