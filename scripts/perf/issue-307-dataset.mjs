#!/usr/bin/env node

import process from "node:process";
import { realpathSync } from "node:fs";
import { fileURLToPath } from "node:url";

const DATASET_ID = "issue-307-v1";
const TEACHER_ID = 307_000;
const PRIMARY_COURSE_ID = 3_071_001;
const HOMEWORK_ID = 3_072_001;

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function sql(value) {
  if (value === null || value === undefined) return "NULL";
  if (typeof value === "number") return String(value);
  if (typeof value === "boolean") return value ? "TRUE" : "FALSE";
  return "'" + String(value).replaceAll("'", "''") + "'";
}

function insertValues(table, columns, rows, updateColumns = []) {
  const values = rows.map((row) => "(" + row.map(sql).join(", ") + ")").join(",\n  ");
  const update = updateColumns.length > 0
    ? "\nON DUPLICATE KEY UPDATE " + updateColumns.map((column) => column + "=VALUES(" + column + ")").join(", ")
    : "";
  return "INSERT INTO " + table + " (" + columns.join(", ") + ") VALUES\n  " + values + update + ";";
}

export function benchmarkFacts() {
  const teacher = { id: TEACHER_ID, username: "perf307_teacher", role: "TEACHER" };
  const students = Array.from({ length: 100 }, (_, index) => ({
    id: TEACHER_ID + index + 1,
    username: "perf307_student" + String(index + 1).padStart(3, "0"),
    role: "STUDENT",
  }));
  const courses = Array.from({ length: 105 }, (_, index) => ({
    id: PRIMARY_COURSE_ID + index,
    name: "性能基准课程 " + String(index + 1).padStart(3, "0"),
  }));
  const members = [teacher, ...students].map((user, index) => ({
    id: 3_077_000 + index,
    courseId: PRIMARY_COURSE_ID,
    userId: user.id,
    role: user.role,
  }));
  const gradeItems = [
    { id: 3_073_001, name: "实验成绩", sourceType: "LAB", sourceId: 3_072_101, weight: 0.4 },
    { id: 3_073_002, name: "作业成绩", sourceType: "HWK", sourceId: HOMEWORK_ID, weight: 0.3 },
    { id: 3_073_003, name: "平时成绩", sourceType: "OTHER_COURSE_ITEM", sourceId: null, weight: 0.3 },
  ];
  const gradeRecords = students.flatMap((student, studentIndex) =>
    gradeItems.map((item, itemIndex) => {
      const score = 80 + ((studentIndex + itemIndex) % 16);
      return {
        id: 307_400_000 + studentIndex * 10 + itemIndex,
        studentId: student.id,
        item,
        score,
        weightedScore: Number((score * item.weight).toFixed(2)),
      };
    }),
  );
  const gradeSummaries = students.map((student, index) => ({
    id: 307_500_000 + index,
    studentId: student.id,
    finalScore: Number((gradeRecords
      .filter((record) => record.studentId === student.id)
      .reduce((sum, record) => sum + record.weightedScore, 0)).toFixed(2)),
  }));
  return {
    datasetId: DATASET_ID,
    users: { teacher, students },
    courses,
    members,
    homework: { id: HOMEWORK_ID, courseId: PRIMARY_COURSE_ID },
    gradeItems,
    gradeRecords,
    gradeSummaries,
  };
}

export function verificationSummary() {
  return "users=101 courses=105 members=101 homeworks=1 summaries=100 grade-records=300 submissions=0";
}

function authSql(facts, microservice) {
  const accounts = [facts.users.teacher, ...facts.users.students];
  const statements = ["-- " + DATASET_ID + " deterministic benchmark identities"];
  for (const account of accounts) {
    const seed = account.role === "TEACHER" ? "teacher001" : "student001";
    const columns = microservice
      ? "user_id, username, user_type, display_name, email, password_hash, password_salt, account_status, security_version, failed_login_count, created_at, updated_at, deleted"
      : "user_id, username, user_type, display_name, email, password_hash, password_salt, account_status, failed_login_count, created_at, updated_at, deleted";
    const values = microservice
      ? [account.id, account.username, account.role, "性能基准" + account.username, account.username + "@example.test", "seed.password_hash", "seed.password_salt", "ACTIVE", 1, 0, "2026-09-02 00:00:00", "2026-09-02 00:00:00", false]
      : [account.id, account.username, account.role, "性能基准" + account.username, account.username + "@example.test", "seed.password_hash", "seed.password_salt", "ACTIVE", 0, "2026-09-02 00:00:00", "2026-09-02 00:00:00", false];
    const rendered = values.map((value) => String(value).startsWith("seed.") ? value : sql(value)).join(", ");
    statements.push(
      "INSERT INTO t_auth_user (" + columns + ")\n" +
      "SELECT " + rendered + " FROM t_auth_user seed WHERE seed.username=" + sql(seed) + "\n" +
      "ON DUPLICATE KEY UPDATE user_type=VALUES(user_type), display_name=VALUES(display_name), " +
      "email=VALUES(email), password_hash=VALUES(password_hash), password_salt=VALUES(password_salt), account_status='ACTIVE', deleted=FALSE;",
    );
    statements.push(
      "INSERT INTO t_auth_user_role (id, user_id, role_id, assigned_by, assigned_at)\n" +
      "SELECT " + (307_800_000 + account.id - TEACHER_ID) + ", " + account.id + ", role_id, " + TEACHER_ID + ", '2026-09-02 00:00:00' " +
      "FROM t_auth_role WHERE role_code=" + sql(account.role) + "\n" +
      "ON DUPLICATE KEY UPDATE role_id=VALUES(role_id), assigned_by=VALUES(assigned_by);",
    );
  }
  return statements.join("\n");
}

function monolithCourseSql(facts) {
  const courseRows = facts.courses.map((course) => [
    course.id, course.name, DATASET_ID, TEACHER_ID, "2026-2027-1", "PERF", null, "PUBLIC", null,
    100, "2026-09-01", "2027-01-31", "ACTIVE", false, "2026-09-02 00:00:00", "2026-09-02 00:00:00",
  ]);
  const memberRows = facts.members.map((member) => [
    member.id, member.courseId, member.userId, member.role, member.role === "TEACHER" ? "CREATED" : "DIRECT",
    "ACTIVE", null, TEACHER_ID, "2026-09-02 00:00:00", null, "2026-09-02 00:00:00", false,
    "2026-09-02 00:00:00", "2026-09-02 00:00:00",
  ]);
  return [
    insertValues("crs_course", ["id", "course_name", "description", "teacher_id", "semester", "category", "cover_url", "enrollment_mode", "invite_code", "max_students", "start_date", "end_date", "status", "is_deleted", "created_at", "updated_at"], courseRows, ["course_name", "description", "teacher_id", "status", "is_deleted"]),
    insertValues("crs_course_member", ["id", "course_id", "user_id", "role", "join_method", "join_status", "apply_reason", "approved_by", "joined_at", "left_at", "last_access_at", "is_deleted", "created_at", "updated_at"], memberRows, ["role", "join_status", "is_deleted"]),
  ].join("\n");
}

function microCourseSql(facts) {
  const courseRows = facts.courses.map((course) => [
    course.id, course.name, DATASET_ID, TEACHER_ID, "2026-2027-1", "PERF", null, "PUBLIC", null,
    100, "2026-09-01", "2027-01-31", "ACTIVE", false, facts.members.length,
    "2026-09-02 00:00:00", "2026-09-02 00:00:00",
  ]);
  const memberRows = facts.members.map((member) => [
    member.id, member.courseId, member.userId, member.role, member.role === "TEACHER" ? "CREATED" : "DIRECT",
    "ACTIVE", null, TEACHER_ID, "2026-09-02 00:00:00", null, "2026-09-02 00:00:00", false,
    1, "2026-09-02 00:00:00", "2026-09-02 00:00:00",
  ]);
  return [
    insertValues("crs_course", ["id", "course_name", "description", "teacher_id", "semester", "category", "cover_url", "enrollment_mode", "invite_code", "max_students", "start_date", "end_date", "status", "is_deleted", "roster_version", "created_at", "updated_at"], courseRows, ["course_name", "description", "teacher_id", "status", "is_deleted", "roster_version"]),
    insertValues("crs_course_member", ["id", "course_id", "user_id", "role", "join_method", "join_status", "apply_reason", "approved_by", "joined_at", "left_at", "last_access_at", "is_deleted", "member_version", "created_at", "updated_at"], memberRows, ["role", "join_status", "is_deleted", "member_version"]),
  ].join("\n");
}

function monolithHomeworkSql() {
  return [
    insertValues("t_hwk_homework", ["id", "course_id", "chapter_id", "title", "description", "type", "status", "total_score", "deadline", "allow_resubmit", "allow_late_submit", "show_evaluation_before_publish", "judge_config_id", "created_by", "published_at", "is_deleted", "created_at", "updated_at"], [[HOMEWORK_ID, PRIMARY_COURSE_ID, null, "性能基准代码作业", DATASET_ID, "CODE", "PUBLISHED", 100, "2027-01-31 23:59:59", true, false, false, 3_072_101, TEACHER_ID, "2026-09-02 00:00:00", false, "2026-09-02 00:00:00", "2026-09-02 00:00:00"]], ["status", "deadline", "allow_resubmit", "is_deleted"]),
    insertValues("t_hwk_judge_config", ["id", "homework_id", "language_limit_json", "time_limit_ms", "memory_limit_kb", "output_compare_mode", "created_at", "updated_at"], [[3_072_101, HOMEWORK_ID, '["python"]', 1000, 65536, "EXACT", "2026-09-02 00:00:00", "2026-09-02 00:00:00"]], ["language_limit_json", "time_limit_ms", "memory_limit_kb"]),
    insertValues("t_hwk_test_case", ["id", "homework_id", "input_data", "expected_output", "score_weight", "is_hidden", "time_limit_ms", "memory_limit_kb", "sort_order", "created_at", "updated_at"], [[3_072_102, HOMEWORK_ID, "", "ok\\n", 100, false, 1000, 65536, 1, "2026-09-02 00:00:00", "2026-09-02 00:00:00"]], ["expected_output", "score_weight"]),
  ].join("\n");
}

function microHomeworkSql(facts) {
  const projections = facts.users.students.map((student) => [PRIMARY_COURSE_ID, student.id, "ACTIVE", 1]);
  return [
    insertValues("assessment_homework", ["id", "course_id", "title", "description", "type", "status", "deadline", "total_score", "allow_resubmit", "allow_late_submit", "allowed_languages", "created_by", "aggregate_version", "published_at", "created_at", "updated_at"], [[HOMEWORK_ID, PRIMARY_COURSE_ID, "性能基准代码作业", DATASET_ID, "CODE", "PUBLISHED", "2027-01-31 23:59:59", 100, true, false, "python", TEACHER_ID, 1, "2026-09-02 00:00:00", "2026-09-02 00:00:00", "2026-09-02 00:00:00"]], ["status", "deadline", "allow_resubmit", "aggregate_version"]),
    insertValues("assessment_homework_testcase", ["id", "homework_id", "input_text", "expected_output", "score_weight", "is_hidden", "sort_order"], [[3_072_102, HOMEWORK_ID, "", "ok\\n", 100, false, 1]], ["expected_output", "score_weight"]),
    insertValues("assessment_course_member_projection", ["course_id", "user_id", "membership_status", "member_version"], projections, ["membership_status", "member_version"]),
    insertValues("assessment_course_membership_watermark", ["course_id", "roster_version"], [[PRIMARY_COURSE_ID, facts.members.length]], ["roster_version"]),
  ].join("\n");
}

function gradeSql(facts) {
  const itemRows = facts.gradeItems.map((item, index) => [
    item.id, PRIMARY_COURSE_ID, item.name, item.sourceType, item.sourceId, 100, item.weight, true, true,
    index + 1, TEACHER_ID, false, "2026-09-02 00:00:00", "2026-09-02 00:00:00",
  ]);
  const recordRows = facts.gradeRecords.map((record) => [
    record.id, PRIMARY_COURSE_ID, record.studentId, record.item.id, record.item.sourceType, record.item.sourceId,
    record.score, record.weightedScore, "SCORED", "PUBLISHED", DATASET_ID, "2026-09-02 00:00:00",
    "2026-09-02 00:00:00", "2026-09-02 00:00:00", "2026-09-02 00:00:00", "2026-09-02 00:00:00",
  ]);
  const summaryRows = facts.gradeSummaries.map((summary) => [
    summary.id, PRIMARY_COURSE_ID, summary.studentId, summary.finalScore, "CALCULATED", "PUBLISHED", null,
    "2026-09-02 00:00:00", "2026-09-02 00:00:00", "2026-09-02 00:00:00",
  ]);
  return [
    insertValues("t_grade_item", ["id", "course_id", "name", "source_type", "source_id", "full_score", "weight", "included_in_final", "enabled", "sort_order", "created_by", "deleted", "created_at", "updated_at"], itemRows, ["name", "weight", "enabled", "deleted"]),
    insertValues("t_grade_record", ["id", "course_id", "student_id", "grade_item_id", "source_type", "source_id", "raw_score", "weighted_score", "grade_status", "publish_status", "comment", "source_updated_at", "calculated_at", "published_at", "created_at", "updated_at"], recordRows, ["source_type", "source_id", "raw_score", "weighted_score", "grade_status", "publish_status"]),
    insertValues("t_course_grade_summary", ["id", "course_id", "student_id", "final_score", "final_status", "publish_status", "calculation_batch_id", "published_at", "created_at", "updated_at"], summaryRows, ["final_score", "final_status", "publish_status"]),
  ].join("\n");
}

function monolithResetSql() {
  return [
    "USE onlinejudge;",
    "DELETE FROM t_hwk_review_log WHERE homework_id=" + HOMEWORK_ID + ";",
    "DELETE FROM t_hwk_evaluation WHERE homework_id=" + HOMEWORK_ID + ";",
    "DELETE FROM t_hwk_submission WHERE homework_id=" + HOMEWORK_ID + ";",
    "ALTER TABLE t_hwk_submission AUTO_INCREMENT=3076001;",
  ].join("\n");
}

function microResetSql() {
  return [
    "USE oj_assessment;",
    "DELETE FROM assessment_homework_review_log WHERE homework_id=" + HOMEWORK_ID + ";",
    "DELETE FROM assessment_homework_evaluation WHERE homework_id=" + HOMEWORK_ID + ";",
    "DELETE FROM assessment_event_outbox;",
    "DELETE FROM evaluation_task WHERE source_type='HWK' AND source_id=" + sql(String(HOMEWORK_ID)) + ";",
    "DELETE FROM assessment_homework_submission WHERE homework_id=" + HOMEWORK_ID + ";",
    "DELETE FROM assessment_submission WHERE source_type='HWK' AND source_id=" + sql(String(HOMEWORK_ID)) + ";",
    "ALTER TABLE assessment_homework_submission AUTO_INCREMENT=3076001;",
  ].join("\n");
}

function verificationSql(architecture) {
  const expected = verificationSummary();
  if (architecture === "monolith") {
    return "USE onlinejudge;\nSELECT CONCAT(" +
      "'users=',(SELECT COUNT(*) FROM t_auth_user WHERE username LIKE 'perf307\\_%' ESCAPE '\\\\')," +
      "' courses=',(SELECT COUNT(*) FROM crs_course WHERE description='" + DATASET_ID + "')," +
      "' members=',(SELECT COUNT(*) FROM crs_course_member WHERE course_id=" + PRIMARY_COURSE_ID + " AND is_deleted=FALSE)," +
      "' homeworks=',(SELECT COUNT(*) FROM t_hwk_homework WHERE id=" + HOMEWORK_ID + " AND is_deleted=FALSE)," +
      "' summaries=',(SELECT COUNT(*) FROM t_course_grade_summary WHERE course_id=" + PRIMARY_COURSE_ID + " AND publish_status='PUBLISHED')," +
      "' grade-records=',(SELECT COUNT(*) FROM t_grade_record WHERE course_id=" + PRIMARY_COURSE_ID + " AND publish_status='PUBLISHED')," +
      "' submissions=',(SELECT COUNT(*) FROM t_hwk_submission WHERE homework_id=" + HOMEWORK_ID + ")) AS verification;\n" +
      "-- expected: " + expected;
  }
  return "SELECT CONCAT(" +
    "'users=',(SELECT COUNT(*) FROM oj_identity.t_auth_user WHERE username LIKE 'perf307\\_%' ESCAPE '\\\\')," +
    "' courses=',(SELECT COUNT(*) FROM oj_course.crs_course WHERE description='" + DATASET_ID + "')," +
    "' members=',(SELECT COUNT(*) FROM oj_course.crs_course_member WHERE course_id=" + PRIMARY_COURSE_ID + " AND is_deleted=FALSE)," +
    "' homeworks=',(SELECT COUNT(*) FROM oj_assessment.assessment_homework WHERE id=" + HOMEWORK_ID + ")," +
    "' summaries=',(SELECT COUNT(*) FROM oj_grade.t_course_grade_summary WHERE course_id=" + PRIMARY_COURSE_ID + " AND publish_status='PUBLISHED')," +
    "' grade-records=',(SELECT COUNT(*) FROM oj_grade.t_grade_record WHERE course_id=" + PRIMARY_COURSE_ID + " AND publish_status='PUBLISHED')," +
    "' submissions=',(SELECT COUNT(*) FROM oj_assessment.assessment_homework_submission WHERE homework_id=" + HOMEWORK_ID + ")) AS verification;\n" +
    "-- expected: " + expected;
}

export function renderDatasetSql({ architecture, phase }) {
  invariant(["monolith", "three-service"].includes(architecture), "architecture must be monolith or three-service");
  invariant(["load", "reset", "verify"].includes(phase), "phase must be load, reset, or verify");
  if (phase === "verify") return verificationSql(architecture) + "\n";
  if (phase === "reset") return (architecture === "monolith" ? monolithResetSql() : microResetSql()) + "\n";

  const facts = benchmarkFacts();
  const blocks = ["-- " + DATASET_ID + " generated fixture; do not add real credentials"];
  if (architecture === "monolith") {
    blocks.push("USE onlinejudge;", monolithResetSql(), authSql(facts, false), monolithCourseSql(facts), monolithHomeworkSql(), gradeSql(facts));
  } else {
    blocks.push("USE oj_assessment;", microResetSql(), "USE oj_identity;", authSql(facts, true), "USE oj_course;", microCourseSql(facts), "USE oj_assessment;", microHomeworkSql(facts), "USE oj_grade;", gradeSql(facts));
  }
  return blocks.join("\n\n") + "\n";
}

function parseArguments(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    invariant(argv[index]?.startsWith("--"), "options must use --name value");
    invariant(argv[index + 1], "missing value for " + argv[index]);
    options[argv[index].slice(2)] = argv[index + 1];
  }
  return options;
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
  try {
    const options = parseArguments(process.argv.slice(2));
    process.stdout.write(renderDatasetSql({ architecture: options.architecture, phase: options.phase }));
  } catch (error) {
    process.stderr.write("issue-307-dataset: " + error.message + "\n");
    process.exitCode = 2;
  }
}
