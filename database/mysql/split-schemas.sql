-- Run as a MySQL administrator. Password-bearing users are provisioned by the
-- deployment secret manager and receive exactly one of these roles.
CREATE DATABASE IF NOT EXISTS oj_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS oj_crs CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS oj_assessment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS oj_learning_grade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE ROLE IF NOT EXISTS 'oj_auth_role', 'oj_crs_role', 'oj_assessment_role', 'oj_learning_grade_role';
GRANT SELECT, INSERT, UPDATE, DELETE ON oj_auth.* TO 'oj_auth_role';
GRANT SELECT, INSERT, UPDATE, DELETE ON oj_crs.* TO 'oj_crs_role';
GRANT SELECT, INSERT, UPDATE, DELETE ON oj_assessment.* TO 'oj_assessment_role';
GRANT SELECT, INSERT, UPDATE, DELETE ON oj_learning_grade.* TO 'oj_learning_grade_role';

CREATE TABLE IF NOT EXISTS oj_auth.schema_migrations LIKE onlinejudge.schema_migrations;
CREATE TABLE IF NOT EXISTS oj_crs.schema_migrations LIKE onlinejudge.schema_migrations;
CREATE TABLE IF NOT EXISTS oj_assessment.schema_migrations LIKE onlinejudge.schema_migrations;
CREATE TABLE IF NOT EXISTS oj_learning_grade.schema_migrations LIKE onlinejudge.schema_migrations;
