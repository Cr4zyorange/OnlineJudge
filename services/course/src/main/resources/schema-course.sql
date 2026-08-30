-- H2 test schema mirrors Course's DB-CRS-01..05 ownership in oj_course.
-- Production uses the versioned MySQL migrations under database/migrations/course.
CREATE TABLE IF NOT EXISTS crs_course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL,
    description VARCHAR(4000),
    teacher_id BIGINT NOT NULL,
    semester VARCHAR(64),
    category VARCHAR(64),
    cover_url VARCHAR(500),
    enrollment_mode VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    invite_code VARCHAR(64),
    max_students INT,
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    roster_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_crs_course_teacher ON crs_course(teacher_id);
CREATE INDEX IF NOT EXISTS idx_crs_course_status ON crs_course(status);

CREATE TABLE IF NOT EXISTS crs_course_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    join_method VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    join_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    apply_reason VARCHAR(500),
    approved_by BIGINT,
    joined_at TIMESTAMP,
    left_at TIMESTAMP,
    last_access_at TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    member_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_course_member UNIQUE(course_id, user_id),
    CONSTRAINT fk_course_member_course FOREIGN KEY(course_id) REFERENCES crs_course(id)
);

CREATE TABLE IF NOT EXISTS crs_chapter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    parent_id BIGINT,
    chapter_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    objective VARCHAR(4000),
    visible_status BOOLEAN NOT NULL DEFAULT TRUE,
    chapter_type INT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_chapter_course FOREIGN KEY(course_id) REFERENCES crs_course(id),
    CONSTRAINT fk_course_chapter_parent FOREIGN KEY(parent_id) REFERENCES crs_chapter(id)
);

CREATE TABLE IF NOT EXISTS crs_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT,
    resource_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    publish_at TIMESTAMP,
    storage_key VARCHAR(500) NOT NULL,
    external_url VARCHAR(1024),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    download_count BIGINT NOT NULL DEFAULT 0,
    upload_user_id BIGINT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_resource_course FOREIGN KEY(course_id) REFERENCES crs_course(id),
    CONSTRAINT fk_course_resource_chapter FOREIGN KEY(chapter_id) REFERENCES crs_chapter(id)
);

CREATE TABLE IF NOT EXISTS crs_announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(16000) NOT NULL,
    is_top BOOLEAN NOT NULL DEFAULT FALSE,
    publisher_id BIGINT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_announcement_course FOREIGN KEY(course_id) REFERENCES crs_course(id)
);

CREATE TABLE IF NOT EXISTS course_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    routing_key VARCHAR(192) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMP,
    lease_generation BIGINT NOT NULL DEFAULT 0,
    last_error VARCHAR(1024),
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS course_membership_reconciliation_checkpoint (
    course_id BIGINT PRIMARY KEY,
    snapshot_event_id VARCHAR(64) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    next_reconcile_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_course_membership_reconciliation_due (next_reconcile_at)
);
