-- DEV/CI ONLY. These disabled identities are SQL health fixtures, not login demo accounts.
-- Rich application demo data remains owned by AuthSeedDataInitializer and IntDemoDataInitializer.
SET NAMES utf8mb4;

INSERT INTO t_auth_user (
    user_id, username, user_type, display_name, password_hash, password_salt,
    account_status, failed_login_count, created_at, updated_at, deleted
) VALUES
    (870287001, 'db_ci_student_287', 'STUDENT', 'D3 数据库学生夹具',
     'DEV_CI_DISABLED_NO_LOGIN', NULL, 'DISABLED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    (870287002, 'db_ci_teacher_287', 'TEACHER', 'D3 数据库教师夹具',
     'DEV_CI_DISABLED_NO_LOGIN', NULL, 'DISABLED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
AS seed_user
ON DUPLICATE KEY UPDATE
    display_name = seed_user.display_name,
    account_status = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

INSERT INTO crs_course (
    id, course_name, description, teacher_id, semester, category, enrollment_mode,
    max_students, status, is_deleted, created_at, updated_at
) VALUES (
    870287, 'D3-DATABASE-287', 'DEV/CI database bootstrap health fixture', 870287002,
    '2026-D3', 'CI_FIXTURE', 'PUBLIC', 2, 'PUBLISHED', FALSE,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
AS seed_course
ON DUPLICATE KEY UPDATE
    course_name = seed_course.course_name,
    description = seed_course.description,
    teacher_id = seed_course.teacher_id,
    status = 'PUBLISHED',
    is_deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO crs_course_member (
    id, course_id, user_id, role, join_method, join_status, joined_at,
    is_deleted, created_at, updated_at
) VALUES
    (870287011, 870287, 870287002, 'TEACHER', 'CREATED', 'ACTIVE', CURRENT_TIMESTAMP,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (870287012, 870287, 870287001, 'STUDENT', 'PUBLIC', 'ACTIVE', CURRENT_TIMESTAMP,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
AS seed_member
ON DUPLICATE KEY UPDATE
    role = seed_member.role,
    join_status = 'ACTIVE',
    joined_at = COALESCE(crs_course_member.joined_at, seed_member.joined_at),
    is_deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;
