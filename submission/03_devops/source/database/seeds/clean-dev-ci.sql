-- Remove only the records owned by database/seeds/dev-ci.sql.
DELETE FROM crs_course_member
 WHERE course_id IN (SELECT id FROM crs_course WHERE course_name = 'D3-DATABASE-287')
    OR user_id IN (
        SELECT user_id FROM t_auth_user
         WHERE username IN ('db_ci_student_287', 'db_ci_teacher_287')
    );

DELETE FROM crs_course
 WHERE course_name = 'D3-DATABASE-287';

DELETE FROM t_auth_user
 WHERE username IN ('db_ci_student_287', 'db_ci_teacher_287');
