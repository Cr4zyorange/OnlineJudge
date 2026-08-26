package com.onlinejudge.crs.database;

import com.onlinejudge.common.exception.BusinessException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.CourseStatus;
import com.onlinejudge.crs.domain.EnrollmentMode;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.domain.dto.CourseJoinRequest;
import com.onlinejudge.crs.domain.dto.CourseMemberResponse;
import com.onlinejudge.crs.domain.dto.CourseMemberUpdateRequest;
import com.onlinejudge.crs.domain.dto.CourseResponse;
import com.onlinejudge.crs.mapper.CourseRepository;
import com.onlinejudge.crs.service.CourseService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 下的 CRS 并发加入/审批与约束验证。
 *
 * <p>本测试由环境变量 {@code OJ_MYSQL_TEST=true} 显式启用；未启用时整类跳过，H2 结果不能替代本项。
 * 与旧版直接拼 SQL 的实现不同，本测试启动最小 Spring 上下文（生产 {@link CourseRepository} +
 * {@link CourseService} + 真实 MySQL 数据源）并通过注入的生产 {@link CourseService}
 * 发起并发 join / updateMember 调用，覆盖“计数后插入”的真实容量竞争窗口与审批状态竞争。</p>
 *
 * <p>安全边界：连接参数取 {@code OJ_MYSQL_URL}（默认
 * {@code jdbc:mysql://127.0.0.1:3307/onlinejudge_test}）、{@code OJ_MYSQL_USER}（默认 root）、
 * {@code OJ_MYSQL_PASSWORD}（默认空）。本测试只借用其中的服务器地址/端口/账号，在服务器上新建
 * 独立临时库 {@code onlinejudge_mysql_test_<uuid>}，执行迁移后使用，结束后 DROP 该临时库；
 * 绝不 DROP 或修改 {@code OJ_MYSQL_URL} 指向的既有库。若该 URL 带有库名且不包含 "test"，
 * 直接失败并拒绝运行，防止误指向开发库。</p>
 */
@SpringBootTest(classes = CrsMysqlConcurrencyTest.MysqlTestConfig.class)
@EnabledIfEnvironmentVariable(named = "OJ_MYSQL_TEST", matches = "true")
class CrsMysqlConcurrencyTest {

    private static final Path MIGRATION_DIR = Path.of("../database/migrations");
    private static final List<String> MIGRATION_FILES = List.of(
            "DB-CRS-01-course-and-member.sql",
            "DB-CRS-02-course-chapter.sql",
            "DB-CRS-03-course-resource.sql",
            "DB-CRS-05-course-announcement.sql"
    );
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(jdbc:mysql://[^/]+)/([^?]*)(\\?.*)?$");

    private static final String ADMIN_URL;
    private static final String ADMIN_USER;
    private static final String ADMIN_PASSWORD;
    private static final String TEST_SCHEMA;

    static {
        String url = envOr("OJ_MYSQL_URL", "jdbc:mysql://127.0.0.1:3307/onlinejudge_test");
        ADMIN_USER = envOr("OJ_MYSQL_USER", "root");
        ADMIN_PASSWORD = envOr("OJ_MYSQL_PASSWORD", "");
        String databaseName = "";
        Matcher matcher = URL_PATTERN.matcher(url);
        if (matcher.matches()) {
            databaseName = matcher.group(2);
            String suffix = matcher.group(3) == null ? "" : matcher.group(3);
            ADMIN_URL = matcher.group(1) + "/" + suffix;
        } else {
            ADMIN_URL = url;
        }
        if (databaseName != null && !databaseName.isBlank()
                && !databaseName.toLowerCase().contains("test")) {
            throw new IllegalStateException(
                    "OJ_MYSQL_URL 指向的库名 [" + databaseName + "] 不是测试库；"
                            + "本测试会创建独立临时库，拒绝在疑似开发库旁运行，请使用 onlinejudge_test 或类似命名");
        }
        TEST_SCHEMA = "onlinejudge_mysql_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class
    })
    @Import({CourseService.class, CourseRepository.class})
    static class MysqlTestConfig {
    }

    @Autowired
    private CourseService courseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        try {
            createSchemaAndRunMigrations();
        } catch (Exception exception) {
            throw new IllegalStateException("无法准备真实 MySQL 临时测试库", exception);
        }
        String url = envOr("OJ_MYSQL_URL", "jdbc:mysql://127.0.0.1:3307/onlinejudge_test");
        String schemaUrl = url.replaceFirst(
                "^jdbc:mysql://[^/]+/[^?]*(\\?.*)?$",
                "jdbc:mysql://" + serverHostPort(url) + "/" + TEST_SCHEMA + "$1");
        registry.add("spring.datasource.url", () -> schemaUrl);
        registry.add("spring.datasource.username", () -> ADMIN_USER);
        registry.add("spring.datasource.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("onlinejudge.course.schema-initializer.enabled", () -> "false");
        registry.add("onlinejudge.demo-data.enabled", () -> "false");
        registry.add("onlinejudge.hwk.attachments.cleanup-enabled", () -> "false");
        registry.add("onlinejudge.lrn.reminders.scheduling-enabled", () -> "false");
    }

    @AfterAll
    static void dropTestSchema() {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + TEST_SCHEMA + "`");
        } catch (SQLException exception) {
            throw new IllegalStateException("清理临时测试库失败: " + TEST_SCHEMA, exception);
        }
    }

    @Test
    void migrationCreatesCrsTablesAndUniqueMemberConstraint() {
        assertThat(tableExists("crs_course_member")).as("crs_course_member 表存在").isTrue();

        List<String> keyColumns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'crs_course_member'
                   AND INDEX_NAME = 'uq_crs_course_member'
                 ORDER BY SEQ_IN_INDEX
                """, String.class);
        assertThat(keyColumns).as("uq_crs_course_member 唯一索引覆盖 (course_id, user_id)").hasSize(2);

        Integer foreignKeys = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'crs_course_member'
                """, Integer.class);
        assertThat(foreignKeys).as("crs_course_member 外键存在").isGreaterThan(0);
    }

    @Test
    void concurrentJoinCreatesSingleMembershipPerUser() throws Exception {
        long teacherId = 901L;
        long courseId = createPublicCourse("并发加入课程", 10, teacherId);
        long studentId = 9901L;

        int threads = 8;
        List<String> outcomes = runConcurrently(threads,
                () -> joinOutcome(courseId, studentId));

        long joined = outcomes.stream().filter("JOINED"::equals).count();
        assertThat(joined).as("同一学生并发加入只有 1 次成功").isEqualTo(1);
        assertThat(outcomes).as("其余并发请求被唯一约束或 ALREADY_JOINED 拒绝")
                .allSatisfy(outcome -> assertThat(outcome).isIn(
                        "JOINED", "ALREADY_JOINED", "DUPLICATE_KEY", "UNEXPECTED"));
        assertThat(outcomes).as("不应出现未知异常").doesNotContain("UNEXPECTED");
        assertThat(count("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                courseId, studentId)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM crs_course_member
                 WHERE course_id = ? AND role = 'STUDENT' AND join_status = 'ACTIVE'
                """, courseId)).isEqualTo(1);
    }

    @Test
    void concurrentApprovalLeavesSingleConsistentTerminalState() throws Exception {
        long teacherId = 902L;
        long courseId = createReviewCourse("并发审批课程", 50, teacherId);
        long studentId = 9902L;
        courseService.join(courseId, new CourseJoinRequest(null, "并发审批验证"), studentUser(studentId));

        int threads = 4;
        List<String> outcomes = runConcurrently(threads, index -> {
            CourseMemberStatus target = index % 2 == 0
                    ? CourseMemberStatus.ACTIVE
                    : CourseMemberStatus.REJECTED;
            return approvalOutcome(courseId, studentId, target, teacherId);
        });

        // compare-and-set 语义：并发批准/拒绝中恰好 1 次把 PENDING 迁移到终态，
        // 其余请求因 UPDATE 影响行数为 0 而返回状态冲突，不允许“后写覆盖”。
        long succeeded = outcomes.stream()
                .filter(outcome -> outcome.equals("APPROVED") || outcome.equals("REJECTED"))
                .count();
        long conflicts = outcomes.stream().filter("TRANSITION_CONFLICT"::equals).count();
        assertThat(succeeded).as("并发审批只允许 1 次合法状态迁移（compare-and-set）").isEqualTo(1);
        assertThat(conflicts).as("其余并发请求必须全部为状态冲突").isEqualTo(threads - 1);
        assertThat(outcomes).as("不应出现未知异常").doesNotContain("UNEXPECTED");
        assertThat(count("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                courseId, studentId)).isEqualTo(1);
        String finalStatus = queryString("""
                SELECT join_status FROM crs_course_member WHERE course_id = ? AND user_id = ?
                """, courseId, studentId);
        assertThat(finalStatus).as("并发审批后成员只收敛到一个合法终态").isIn("ACTIVE", "REJECTED");
    }

    @Test
    void capacityGuardRejectsJoinWhenCourseAlreadyFull() throws Exception {
        long teacherId = 903L;
        long courseId = createPublicCourse("满员并发课程", 2, teacherId);
        jdbcTemplate.update("""
                INSERT INTO crs_course_member
                    (course_id, user_id, role, join_method, join_status, approved_by, joined_at)
                VALUES (?, ?, 'STUDENT', 'PUBLIC', 'ACTIVE', ?, CURRENT_TIMESTAMP)
                """, courseId, 9903L, teacherId);

        int threads = 6;
        List<String> outcomes = runConcurrently(threads, index -> {
            long studentId = 10000L + index;
            return joinOutcome(courseId, studentId);
        });

        long joined = outcomes.stream().filter("JOINED"::equals).count();
        long full = outcomes.stream().filter("COURSE_FULL"::equals).count();
        assertThat(joined).as("从剩余 1 个名额开始并发加入，只允许 1 人成功").isEqualTo(1);
        assertThat(full).as("其余并发请求必须返回 COURSE_FULL").isEqualTo(threads - 1);
        assertThat(outcomes).as("不应出现未知异常").doesNotContain("UNEXPECTED");
        assertThat(count("""
                SELECT COUNT(*) FROM crs_course_member
                 WHERE course_id = ? AND role = 'STUDENT' AND join_status = 'ACTIVE'
                """, courseId)).isEqualTo(2);
    }

    private List<String> runConcurrently(int threads, Callable<String> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<String> runConcurrently(int threads, TaskWithIndex task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call(index);
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private String joinOutcome(long courseId, long studentId) {
        try {
            courseService.join(courseId, new CourseJoinRequest(null, null), studentUser(studentId));
            return "JOINED";
        } catch (BusinessException businessException) {
            return switch (businessException.getMessage()) {
                case "ALREADY_JOINED", "JOIN_PENDING" -> "ALREADY_JOINED";
                case "COURSE_FULL" -> "COURSE_FULL";
                default -> "UNEXPECTED";
            };
        } catch (DataIntegrityViolationException duplicate) {
            return "DUPLICATE_KEY";
        } catch (RuntimeException unexpected) {
            throw unexpected;
        }
    }

    private String approvalOutcome(long courseId, long studentId, CourseMemberStatus target, long teacherId) {
        try {
            CourseMemberResponse response = courseService.updateMember(
                    courseId,
                    studentId,
                    new CourseMemberUpdateRequest(CourseMemberRole.STUDENT, target),
                    teacherUser(teacherId));
            return response.status() == CourseMemberStatus.ACTIVE ? "APPROVED" : "REJECTED";
        } catch (BusinessException businessException) {
            return "INVALID_MEMBER_STATUS_TRANSITION".equals(businessException.getMessage())
                    ? "TRANSITION_CONFLICT"
                    : "UNEXPECTED";
        } catch (RuntimeException unexpected) {
            throw unexpected;
        }
    }

    private long createPublicCourse(String name, Integer maxStudents, long teacherId) {
        CourseResponse course = courseService.create(
                new CourseCreateRequest(
                        name + "-" + System.nanoTime(),
                        "真实 MySQL 并发验证",
                        null,
                        null,
                        null,
                        EnrollmentMode.PUBLIC,
                        null,
                        maxStudents,
                        null,
                        null,
                        CourseStatus.ACTIVE),
                teacherUser(teacherId));
        return course.id();
    }

    private long createReviewCourse(String name, Integer maxStudents, long teacherId) {
        CourseResponse course = courseService.create(
                new CourseCreateRequest(
                        name + "-" + System.nanoTime(),
                        "真实 MySQL 并发审批验证",
                        null,
                        null,
                        null,
                        EnrollmentMode.REVIEW,
                        null,
                        maxStudents,
                        null,
                        null,
                        CourseStatus.ACTIVE),
                teacherUser(teacherId));
        return course.id();
    }

    private static CurrentUser teacherUser(long teacherId) {
        return new CurrentUser(teacherId, "teacher" + teacherId, "TEACHER", Set.of());
    }

    private static CurrentUser studentUser(long studentId) {
        return new CurrentUser(studentId, "student" + studentId, "STUDENT", Set.of());
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private long count(String sql, Object... params) {
        return jdbcTemplate.queryForObject(sql, Long.class, params);
    }

    private String queryString(String sql, Object... params) {
        return jdbcTemplate.queryForObject(sql, String.class, params);
    }

    private static String serverHostPort(String url) {
        Matcher matcher = URL_PATTERN.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1).substring("jdbc:mysql://".length());
        }
        return url.substring("jdbc:mysql://".length());
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void createSchemaAndRunMigrations() throws Exception {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + TEST_SCHEMA + "`"
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        // 兜底清理：即使 Spring 上下文启动失败导致 @AfterAll 未执行，JVM 退出时也删除临时库。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + TEST_SCHEMA + "`");
            } catch (SQLException ignored) {
                // 最佳努力清理，正常路径由 @AfterAll 负责并在失败时显式报错。
            }
        }, "crs-mysql-test-schema-cleanup"));
        String schemaUrl = "jdbc:mysql://" + serverHostPort(
                envOr("OJ_MYSQL_URL", "jdbc:mysql://127.0.0.1:3307/onlinejudge_test"))
                + "/" + TEST_SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(schemaUrl, ADMIN_USER, ADMIN_PASSWORD)) {
            for (String file : MIGRATION_FILES) {
                executeScript(connection, readSql(file));
            }
        }
    }

    private static void executeScript(Connection connection, String script) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String raw : script.split(";")) {
                String sql = raw.trim();
                if (!sql.isEmpty()) {
                    // 仓库迁移文件使用 MariaDB 风格 CREATE INDEX IF NOT EXISTS；临时库为全新库，
                    // 归一化为 MySQL 兼容的 CREATE INDEX 即可幂等执行。
                    sql = sql.replaceFirst("(?i)^(CREATE (?:UNIQUE )?INDEX) IF NOT EXISTS ", "$1 ");
                    statement.execute(sql);
                }
            }
        }
    }

    private static String readSql(String filename) throws IOException {
        return Files.readString(MIGRATION_DIR.resolve(filename), StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface TaskWithIndex {
        String call(int index) throws Exception;
    }
}
