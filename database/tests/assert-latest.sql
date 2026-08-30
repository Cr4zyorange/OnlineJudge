DELIMITER $$

DROP PROCEDURE IF EXISTS assert_onlinejudge_database_contract$$
CREATE PROCEDURE assert_onlinejudge_database_contract()
BEGIN
    DECLARE actual_count INT DEFAULT 0;

    SELECT COUNT(*) INTO actual_count FROM schema_migrations WHERE success = 1;
    IF actual_count <> 28 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'schema_migrations must contain 28 successful versions';
    END IF;

    SELECT COUNT(*) INTO actual_count
      FROM t_auth_user
     WHERE username IN ('db_ci_student_287', 'db_ci_teacher_287')
       AND account_status = 'DISABLED';
    IF actual_count <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DEV/CI disabled test identities are missing';
    END IF;

    SELECT COUNT(*) INTO actual_count
      FROM crs_course c
      JOIN crs_course_member m ON m.course_id = c.id
     WHERE c.course_name = 'D3-DATABASE-287'
       AND m.join_status = 'ACTIVE';
    IF actual_count <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DEV/CI course membership fixture is incomplete';
    END IF;

    SELECT COUNT(*) INTO actual_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_auth_user'
       AND index_name = 'uk_auth_user_username'
       AND non_unique = 0;
    IF actual_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH username unique constraint is missing';
    END IF;

    SELECT COUNT(*) INTO actual_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_hwk_submission'
       AND index_name = 'idx_hwk_submission_attention';
    IF actual_count <> 10 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HWK attention index columns are incomplete';
    END IF;

    SELECT COUNT(*) INTO actual_count
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 't_hwk_submission_attachment'
       AND constraint_name = 'fk_hwk_attachment_submission';
    IF actual_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'HWK attachment submission foreign key is missing';
    END IF;

    SELECT COUNT(*) INTO actual_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND column_name IN (
           'source_fingerprint', 'total_student_count', 'completed_count',
           'missing_count', 'unsubmitted_count', 'ungraded_count'
       );
    IF actual_count <> 6 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'GRD analysis upgrade columns are incomplete';
    END IF;

    SELECT COUNT(*) INTO actual_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           'assessment_event_outbox', 'course_event_outbox', 'grade_event_outbox',
           'assessment_event_inbox', 'grade_event_inbox', 'learning_event_inbox',
           'learning_event_dead_letter', 'learning_event_reconciliation_request',
           'learning_course_member_projection'
       );
    IF actual_count <> 9 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reliable messaging ownership tables are incomplete';
    END IF;

    SELECT 'database_contract_ok' AS result;
END$$

CALL assert_onlinejudge_database_contract()$$
DROP PROCEDURE assert_onlinejudge_database_contract$$

DELIMITER ;
