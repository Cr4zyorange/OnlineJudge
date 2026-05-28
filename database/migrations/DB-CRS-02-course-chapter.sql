CREATE TABLE IF NOT EXISTS crs_chapter (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NULL,
    order_num INT NOT NULL DEFAULT 0,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_chapter_course_parent_order (course_id, parent_id, order_num),
    INDEX idx_crs_chapter_parent (parent_id),
    CONSTRAINT fk_crs_chapter_course FOREIGN KEY (course_id) REFERENCES crs_course (id),
    CONSTRAINT fk_crs_chapter_parent FOREIGN KEY (parent_id) REFERENCES crs_chapter (id)
);
