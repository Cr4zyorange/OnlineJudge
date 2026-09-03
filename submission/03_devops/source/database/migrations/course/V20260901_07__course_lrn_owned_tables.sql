-- #306: LRN is Course-owned.  These are the complete tables used by the
-- existing Course-owned LRN repositories and reliable-message consumers.
CREATE TABLE IF NOT EXISTS lrn_learning_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    deadline DATETIME NULL,
    progress INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    action_url VARCHAR(500) NULL,
    snapshot_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_task_user_course (user_id, course_id),
    KEY idx_lrn_task_user_type_status_deadline (user_id, task_type, status, deadline),
    KEY idx_lrn_task_status_deadline (status, deadline),
    KEY idx_lrn_task_source (source_module, source_id)
);

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

CREATE TABLE IF NOT EXISTS lrn_learning_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    duration INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_record_user_started (user_id, started_at),
    KEY idx_lrn_record_user_course_started (user_id, course_id, started_at),
    KEY idx_lrn_record_rate_limit (user_id, course_id, source_module, source_id, created_at)
);

CREATE TABLE IF NOT EXISTS lrn_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    idempotency_key VARCHAR(128) NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 1,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    source_module VARCHAR(20) NOT NULL,
    source_id BIGINT NULL,
    action_url VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_lrn_notification_idempotency_user (idempotency_key, user_id),
    KEY idx_lrn_notification_user_created (user_id, created_at),
    KEY idx_lrn_notification_user_type_created (user_id, type, created_at),
    KEY idx_lrn_notification_user_read_created (user_id, is_read, created_at),
    KEY idx_lrn_notification_course (course_id)
);

CREATE TABLE IF NOT EXISTS lrn_notification_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    old_status VARCHAR(32) NULL,
    new_status VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lrn_notification_status_notification (notification_id),
    KEY idx_lrn_notification_status_user_time (user_id, operated_at),
    KEY idx_lrn_notification_status_operation (operation_type)
);

CREATE TABLE IF NOT EXISTS lrn_reminder_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reminder_type VARCHAR(32) NOT NULL,
    source_module VARCHAR(20) NOT NULL,
    ahead_minutes INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_reminder_rule_user_scope (user_id, reminder_type, source_module, ahead_minutes),
    KEY idx_lrn_reminder_rule_user (user_id),
    KEY idx_lrn_reminder_rule_type_enabled (reminder_type, source_module, enabled)
);

CREATE TABLE IF NOT EXISTS lrn_notification_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    enable_experiment BOOLEAN NOT NULL DEFAULT TRUE,
    enable_homework BOOLEAN NOT NULL DEFAULT TRUE,
    enable_grade BOOLEAN NOT NULL DEFAULT TRUE,
    enable_announcement BOOLEAN NOT NULL DEFAULT TRUE,
    enable_non_critical_reminder BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_notification_setting_user (user_id)
);

CREATE TABLE IF NOT EXISTS lrn_reminder_scan_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    scan_started_at DATETIME NOT NULL,
    scan_ended_at DATETIME NULL,
    triggered_count INT NOT NULL DEFAULT 0,
    failed_reason VARCHAR(500) NULL,
    retry_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lrn_reminder_scan_batch (batch_id),
    KEY idx_lrn_reminder_scan_started (scan_started_at),
    KEY idx_lrn_reminder_scan_retry (retry_status)
);

CREATE TABLE IF NOT EXISTS learning_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learning_event_inbox_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_learning_event_inbox_aggregate (consumer_name, aggregate_type, aggregate_id, aggregate_version)
);

CREATE TABLE IF NOT EXISTS learning_event_delivery_attempt (
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    attempt_count INT NOT NULL,
    last_error VARCHAR(1024) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS learning_event_dead_letter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    envelope_json TEXT NOT NULL,
    failure_classification VARCHAR(64) NOT NULL,
    failure_message VARCHAR(1024) NOT NULL,
    attempt_count INT NOT NULL,
    replayed_at TIMESTAMP NULL,
    replayed_by VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learning_dead_letter_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_learning_dead_letter_created (created_at),
    KEY idx_learning_dead_letter_correlation (correlation_id)
);

CREATE TABLE IF NOT EXISTS learning_event_reconciliation_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    observed_version BIGINT NOT NULL,
    last_applied_version BIGINT NOT NULL,
    triggering_event_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    request_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT uq_learning_reconciliation_gap UNIQUE (consumer_name, aggregate_type, aggregate_id, observed_version),
    KEY idx_learning_reconciliation_open (request_status, created_at)
);

CREATE TABLE IF NOT EXISTS learning_deferred_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    envelope_json TEXT NOT NULL,
    deferral_reason VARCHAR(64) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP NULL,
    last_error VARCHAR(1024) NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learning_deferred_consumer_event UNIQUE (consumer_name, event_id),
    KEY idx_learning_deferred_due (delivery_status, next_attempt_at, lease_until),
    KEY idx_learning_deferred_correlation (correlation_id)
);

CREATE TABLE IF NOT EXISTS learning_course_member_projection (
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    membership_status VARCHAR(32) NOT NULL,
    member_version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (course_id, user_id),
    KEY idx_learning_course_member_active (course_id, membership_status)
);

CREATE TABLE IF NOT EXISTS learning_course_membership_watermark (
    course_id BIGINT NOT NULL PRIMARY KEY,
    snapshot_version BIGINT NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_learning_roster_watermark_version (snapshot_version)
);

-- A migration principal, never the oj_course_rw runtime account, may execute
-- this one-time source read.  The ledger row for V20260901_07 is written only
-- after every detected legacy table is copied and its row count/content digest
-- matches the Course target.  An absent source is the normal fresh-install
-- case; an existing source must be cut over while writers are quiesced.
DROP PROCEDURE IF EXISTS course_copy_legacy_lrn_table;
DELIMITER //
CREATE PROCEDURE course_copy_legacy_lrn_table(IN table_to_copy VARCHAR(64))
copy_legacy: BEGIN
    DECLARE source_table_exists INT DEFAULT 0;
    DECLARE column_expression LONGTEXT;
    DECLARE error_message VARCHAR(255);

    SELECT COUNT(*) INTO source_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'oj_learning' AND table_name = table_to_copy;
    IF source_table_exists = 0 THEN
        LEAVE copy_legacy;
    END IF;

    SELECT GROUP_CONCAT(
               CONCAT('COALESCE(CAST(`', column_name, '` AS CHAR), ''<NULL>'')')
               ORDER BY ordinal_position SEPARATOR ', '
           ) INTO column_expression
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = table_to_copy;
    IF column_expression IS NULL THEN
        SET error_message = CONCAT('missing Course target table: ', table_to_copy);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;

    SET @course_lrn_sql = CONCAT(
        'REPLACE INTO `', DATABASE(), '`.`', table_to_copy,
        '` SELECT * FROM `oj_learning`.`', table_to_copy, '`'
    );
    PREPARE course_lrn_statement FROM @course_lrn_sql;
    EXECUTE course_lrn_statement;
    DEALLOCATE PREPARE course_lrn_statement;

    SET @course_lrn_sql = CONCAT(
        'SELECT COUNT(*), COALESCE(BIT_XOR(CRC32(CONCAT_WS(CHAR(31), ', column_expression,
        '))), 0) INTO @course_lrn_source_count, @course_lrn_source_digest FROM `oj_learning`.`', table_to_copy, '`'
    );
    PREPARE course_lrn_statement FROM @course_lrn_sql;
    EXECUTE course_lrn_statement;
    DEALLOCATE PREPARE course_lrn_statement;

    SET @course_lrn_sql = CONCAT(
        'SELECT COUNT(*), COALESCE(BIT_XOR(CRC32(CONCAT_WS(CHAR(31), ', column_expression,
        '))), 0) INTO @course_lrn_target_count, @course_lrn_target_digest FROM `', DATABASE(), '`.`', table_to_copy, '`'
    );
    PREPARE course_lrn_statement FROM @course_lrn_sql;
    EXECUTE course_lrn_statement;
    DEALLOCATE PREPARE course_lrn_statement;

    IF @course_lrn_source_count <> @course_lrn_target_count
       OR @course_lrn_source_digest <> @course_lrn_target_digest THEN
        SET error_message = CONCAT('legacy LRN cutover validation failed for ', table_to_copy);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END//
DELIMITER ;

CALL course_copy_legacy_lrn_table('lrn_learning_task');
CALL course_copy_legacy_lrn_table('lrn_learning_progress');
CALL course_copy_legacy_lrn_table('lrn_learning_record');
CALL course_copy_legacy_lrn_table('lrn_notification');
CALL course_copy_legacy_lrn_table('lrn_notification_status_log');
CALL course_copy_legacy_lrn_table('lrn_reminder_rule');
CALL course_copy_legacy_lrn_table('lrn_notification_setting');
CALL course_copy_legacy_lrn_table('lrn_reminder_scan_log');
CALL course_copy_legacy_lrn_table('learning_event_inbox');
CALL course_copy_legacy_lrn_table('learning_event_delivery_attempt');
CALL course_copy_legacy_lrn_table('learning_event_dead_letter');
CALL course_copy_legacy_lrn_table('learning_event_reconciliation_request');
CALL course_copy_legacy_lrn_table('learning_deferred_event');
CALL course_copy_legacy_lrn_table('learning_course_member_projection');
CALL course_copy_legacy_lrn_table('learning_course_membership_watermark');
DROP PROCEDURE course_copy_legacy_lrn_table;
