CREATE TABLE IF NOT EXISTS crs_course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    teacher_id BIGINT NOT NULL,
    enrollment_mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    roster_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS crs_course_member (
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    member_version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (course_id, user_id),
    CONSTRAINT fk_course_member_course FOREIGN KEY (course_id) REFERENCES crs_course(id)
);

CREATE TABLE IF NOT EXISTS crs_chapter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_chapter_course FOREIGN KEY (course_id) REFERENCES crs_course(id)
);

CREATE TABLE IF NOT EXISTS crs_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    resource_url VARCHAR(1024) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_resource_course FOREIGN KEY (course_id) REFERENCES crs_course(id)
);

-- Same durable Course-owned outbox shape as #337; it is deliberately not a shared table.
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
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP NULL,
    last_error VARCHAR(1024) NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A Course-owned durable watermark.  Reconciliation may run on each restart,
-- but it must not manufacture a new roster version for unchanged course data.
CREATE TABLE IF NOT EXISTS course_roster_reconciliation_checkpoint (
    course_id BIGINT PRIMARY KEY,
    emitted_roster_version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_roster_checkpoint_course FOREIGN KEY (course_id) REFERENCES crs_course(id)
);
