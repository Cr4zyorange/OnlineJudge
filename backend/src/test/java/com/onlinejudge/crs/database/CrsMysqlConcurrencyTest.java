package com.onlinejudge.crs.database;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 下的 CRS 并发加入/审批与约束验证。
 * 由环境变量 OJ_MYSQL_TEST=true 显式启用；未启用时整类跳过，H2 结果不能替代本项验证。
 * 连接参数：OJ_MYSQL_URL（默认 jdbc:mysql://127.0.0.1:3307/onlinejudge_test）、
 * OJ_MYSQL_USER（默认 root）、OJ_MYSQL_PASSWORD（默认空）。
 */
@EnabledIfEnvironmentVariable(named = "OJ_MYSQL_TEST", matches = "true")
class CrsMysqlConcurrencyTest {

    private static final Path MIGRATION_DIR = Path.of("../database/migrations");
    private static Connection connection;

    @BeforeAll
    static void prepareSchema() throws Exception {
        connection = openConnection();
        execute("DROP TABLE IF EXISTS crs_announcement");
        execute("DROP TABLE IF EXISTS crs_resource");
        execute("DROP TABLE IF EXISTS crs_chapter");
        execute("DROP TABLE IF EXISTS crs_course_member");
        execute("DROP TABLE IF EXISTS crs_course");
        for (String file : List.of(
                "DB-CRS-01-course-and-member.sql",
                "DB-CRS-02-course-chapter.sql",
                "DB-CRS-03-course-resource.sql",
                "DB-CRS-05-course-announcement.sql")) {
            executeScript(readSql(file));
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void migrationCreatesCrsTablesAndUniqueMemberConstraint() throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "crs_course_member", null)) {
            assertThat(tables.next()).as("crs_course_member 表存在").isTrue();
        }
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("""
                     SHOW INDEX FROM crs_course_member
                     WHERE Key_name = 'uq_crs_course_member'
                     """)) {
            int columns = 0;
            while (indexes.next()) {
                columns++;
            }
            assertThat(columns).as("uq_crs_course_member 唯一索引覆盖 (course_id, user_id)").isEqualTo(2);
        }
        try (Statement statement = connection.createStatement();
             ResultSet fks = statement.executeQuery("""
                     SELECT CONSTRAINT_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS
                     WHERE CONSTRAINT_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'crs_course_member'
                     """)) {
            assertThat(fks.next()).as("crs_course_member 外键存在").isTrue();
        }
    }

    @Test
    void concurrentJoinCreatesSingleMembershipPerUser() throws Exception {
        long courseId = insertCourse("并发加入课程", "PUBLIC", null, 10);
        insertMember(courseId, 901L, "TEACHER", "ACTIVE", "CREATED", null);
        long studentId = 9901L;

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    try (Connection conn = openConnection()) {
                        conn.setAutoCommit(false);
                        try {
                            insertMember(conn, courseId, studentId, "STUDENT", "ACTIVE", "PUBLIC", null);
                            conn.commit();
                            return true;
                        } catch (SQLException duplicate) {
                            conn.rollback();
                            if (duplicate.getSQLState() != null && duplicate.getSQLState().startsWith("23")) {
                                return false;
                            }
                            throw duplicate;
                        }
                    }
                });
            }
            List<Future<Boolean>> results = executor.invokeAll(tasks);
            long successCount = results.stream().filter(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).count();

            assertThat(successCount).as("并发加入只有 1 次成功").isEqualTo(1);
            assertThat(countRows("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                    courseId, studentId)).isEqualTo(1);
            assertThat(countRows("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND role = 'STUDENT' AND join_status = 'ACTIVE'",
                    courseId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentApprovalLeavesSingleConsistentTerminalState() throws Exception {
        long courseId = insertCourse("并发审批课程", "REVIEW", null, 50);
        insertMember(courseId, 902L, "TEACHER", "ACTIVE", "CREATED", null);
        long studentId = 9902L;
        insertMember(courseId, studentId, "STUDENT", "PENDING", "REVIEW", null);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                boolean approve = i % 2 == 0;
                tasks.add(() -> approveWithServiceLikeCheck(courseId, studentId, approve));
            }
            List<Future<String>> results = executor.invokeAll(tasks);
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : results) {
                outcomes.add(future.get());
            }

            assertThat(outcomes).as("审批结果只能为 APPROVED/REJECTED/PRECONDITION_FAILED").allSatisfy(outcome ->
                    assertThat(outcome).isIn("APPROVED", "REJECTED", "PRECONDITION_FAILED"));
            assertThat(outcomes).contains("APPROVED").contains("REJECTED");
            assertThat(countRows("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                    courseId, studentId)).isEqualTo(1);
            String finalStatus = queryString("SELECT join_status FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                    courseId, studentId);
            assertThat(finalStatus).as("并发审批后成员状态一致").isIn("ACTIVE", "REJECTED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void capacityGuardRejectsJoinWhenCourseAlreadyFull() throws Exception {
        long courseId = insertCourse("满员并发课程", "PUBLIC", 2, 93);
        insertMember(courseId, 903L, "TEACHER", "ACTIVE", "CREATED", null);
        insertMember(courseId, 9903L, "STUDENT", "ACTIVE", "PUBLIC", null);
        insertMember(courseId, 9904L, "STUDENT", "ACTIVE", "PUBLIC", null);

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                long studentId = 10000L + i;
                tasks.add(() -> joinWithCapacityCheck(courseId, studentId));
            }
            List<Future<String>> results = executor.invokeAll(tasks);
            for (Future<String> future : results) {
                assertThat(future.get()).isEqualTo("COURSE_FULL");
            }
            assertThat(countRows("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND role = 'STUDENT' AND join_status = 'ACTIVE'",
                    courseId)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private String approveWithServiceLikeCheck(long courseId, long studentId, boolean approve) throws Exception {
        try (Connection conn = openConnection()) {
            String currentStatus = queryString(conn,
                    "SELECT join_status FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                    courseId, studentId);
            if (approve) {
                if (!"PENDING".equals(currentStatus) && !"ACTIVE".equals(currentStatus)) {
                    return "PRECONDITION_FAILED";
                }
                execute(conn,
                        "UPDATE crs_course_member SET join_status = 'ACTIVE', approved_by = ?, joined_at = CURRENT_TIMESTAMP WHERE course_id = ? AND user_id = ?",
                        902L, courseId, studentId);
                return "APPROVED";
            }
            if (!"PENDING".equals(currentStatus)) {
                return "PRECONDITION_FAILED";
            }
            execute(conn,
                    "UPDATE crs_course_member SET join_status = 'REJECTED' WHERE course_id = ? AND user_id = ?",
                    courseId, studentId);
            return "REJECTED";
        }
    }

    private String joinWithCapacityCheck(long courseId, long studentId) throws Exception {
        try (Connection conn = openConnection()) {
            Integer maxStudents = queryInt(conn, "SELECT max_students FROM crs_course WHERE id = ?", courseId);
            int activeStudents = queryInt(conn,
                    "SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND role = 'STUDENT' AND join_status = 'ACTIVE'",
                    courseId);
            if (maxStudents != null && activeStudents >= maxStudents) {
                return "COURSE_FULL";
            }
            insertMember(conn, courseId, studentId, "STUDENT", "ACTIVE", "PUBLIC", null);
            return "JOINED";
        }
    }

    private static Connection openConnection() throws SQLException {
        String url = envOr("OJ_MYSQL_URL", "jdbc:mysql://127.0.0.1:3307/onlinejudge_test");
        String user = envOr("OJ_MYSQL_USER", "root");
        String password = envOr("OJ_MYSQL_PASSWORD", "");
        return DriverManager.getConnection(url, user, password);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private long insertCourse(String name, String enrollmentMode, Integer maxStudents, int teacherId) throws Exception {
        try (Connection conn = openConnection()) {
            execute(conn, """
                    INSERT INTO crs_course (course_name, teacher_id, enrollment_mode, max_students, status)
                    VALUES (?, ?, ?, ?, 'ACTIVE')
                    """, name, teacherId, enrollmentMode, maxStudents);
            return queryLong(conn, "SELECT MAX(id) FROM crs_course WHERE course_name = ?", name);
        }
    }

    private void insertMember(long courseId, long userId, String role, String status, String method, Long approvedBy) throws Exception {
        try (Connection conn = openConnection()) {
            insertMember(conn, courseId, userId, role, status, method, approvedBy);
        }
    }

    private void insertMember(Connection conn, long courseId, long userId, String role, String status, String method, Long approvedBy) throws SQLException {
        execute(conn, """
                INSERT INTO crs_course_member (course_id, user_id, role, join_method, join_status, approved_by, joined_at)
                VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, courseId, userId, role, method, status, approvedBy, status);
    }

    private static String readSql(String filename) throws IOException {
        return Files.readString(MIGRATION_DIR.resolve(filename), StandardCharsets.UTF_8);
    }

    private static void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeScript(String script) throws SQLException {
        for (String statement : script.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                execute(trimmed);
            }
        }
    }

    private static void execute(Connection conn, String sql, Object... params) throws SQLException {
        try (var prepared = conn.prepareStatement(sql)) {
            bind(prepared, params);
            prepared.executeUpdate();
        }
    }

    private static void bind(java.sql.PreparedStatement prepared, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            prepared.setObject(i + 1, params[i]);
        }
    }

    private long countRows(String sql, Object... params) throws SQLException {
        try (var prepared = connection.prepareStatement(sql)) {
            bind(prepared, params);
            try (ResultSet resultSet = prepared.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private int queryInt(Connection conn, String sql, Object... params) throws SQLException {
        Number value = queryNumber(conn, sql, params);
        return value == null ? 0 : value.intValue();
    }

    private long queryLong(Connection conn, String sql, Object... params) throws SQLException {
        Number value = queryNumber(conn, sql, params);
        if (value == null) {
            throw new IllegalStateException("查询未返回主键");
        }
        return value.longValue();
    }

    private Number queryNumber(Connection conn, String sql, Object... params) throws SQLException {
        try (var prepared = conn.prepareStatement(sql)) {
            bind(prepared, params);
            try (ResultSet resultSet = prepared.executeQuery()) {
                resultSet.next();
                return (Number) resultSet.getObject(1);
            }
        }
    }

    private String queryString(String sql, Object... params) throws SQLException {
        try (var prepared = connection.prepareStatement(sql)) {
            bind(prepared, params);
            try (ResultSet resultSet = prepared.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private String queryString(Connection conn, String sql, Object... params) throws SQLException {
        try (var prepared = conn.prepareStatement(sql)) {
            bind(prepared, params);
            try (ResultSet resultSet = prepared.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }
}
