-- #312 Course-owned schema.  Its DB-CRS-01..05 names and fields deliberately
-- match the #341 copied Course facts in oj_course; this is not a second,
-- simplified crs_* layout.  V20260831_03 adds only v2 runtime versions when
-- the #341 target was copied from the legacy schema.
CREATE TABLE IF NOT EXISTS crs_course (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    teacher_id BIGINT NOT NULL,
    semester VARCHAR(64) NULL,
    category VARCHAR(64) NULL,
    cover_url VARCHAR(500) NULL,
    enrollment_mode VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    invite_code VARCHAR(64) NULL,
    max_students INT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    roster_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_course_teacher (teacher_id),
    INDEX idx_crs_course_status (status),
    INDEX idx_crs_course_name (course_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crs_course_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    join_method VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    join_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    apply_reason VARCHAR(500) NULL,
    approved_by BIGINT NULL,
    joined_at DATETIME NULL,
    left_at DATETIME NULL,
    last_access_at DATETIME NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    member_version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_crs_course_member (course_id, user_id),
    INDEX idx_crs_member_course (course_id),
    INDEX idx_crs_member_user (user_id),
    INDEX idx_crs_member_role_status (role, join_status),
    CONSTRAINT fk_crs_member_course FOREIGN KEY (course_id) REFERENCES crs_course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crs_chapter (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    chapter_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    objective TEXT NULL,
    visible_status TINYINT(1) NOT NULL DEFAULT 1,
    chapter_type TINYINT(1) NOT NULL DEFAULT 1,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_chapter_course_parent_order (course_id, parent_id, sort_order),
    INDEX idx_crs_chapter_parent (parent_id),
    CONSTRAINT fk_crs_chapter_course FOREIGN KEY (course_id) REFERENCES crs_course(id),
    CONSTRAINT fk_crs_chapter_parent FOREIGN KEY (parent_id) REFERENCES crs_chapter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crs_resource (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    resource_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    publish_at DATETIME NULL,
    storage_key VARCHAR(500) NOT NULL,
    external_url VARCHAR(1024) NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    download_count BIGINT NOT NULL DEFAULT 0,
    upload_user_id BIGINT NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_resource_course (course_id),
    INDEX idx_crs_resource_chapter (chapter_id),
    INDEX idx_crs_resource_uploader (upload_user_id),
    CONSTRAINT fk_crs_resource_course FOREIGN KEY (course_id) REFERENCES crs_course(id),
    CONSTRAINT fk_crs_resource_chapter FOREIGN KEY (chapter_id) REFERENCES crs_chapter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crs_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    CONSTRAINT fk_crs_announcement_course FOREIGN KEY (course_id) REFERENCES crs_course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    routing_key VARCHAR(192) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until DATETIME NULL,
    last_error VARCHAR(1024) NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_course_event_outbox_event_id (event_id),
    KEY idx_course_event_outbox_due (delivery_status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_membership_reconciliation_checkpoint (
    course_id BIGINT NOT NULL PRIMARY KEY,
    snapshot_event_id VARCHAR(64) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    next_reconcile_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_course_membership_reconciliation_due (next_reconcile_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
