package com.onlinejudge.courseservice;

import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.service.CourseService;
import com.onlinejudge.courseservice.web.CourseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs only in verify-course-service-live.sh against disposable MySQL 8.4.
 * Two concurrent sibling creates at the same sortOrder must end with exactly
 * one persisted row: the service pre-check catches the sequential case, and
 * uq_crs_chapter_active_order turns the write race into the same 409.
 */
@SpringBootTest(classes = CourseServiceApplication.class)
@EnabledIfSystemProperty(named = "course.test.mysql", matches = "true")
class CourseChapterOrderConcurrencyMySqlTest {
    private static final CurrentUser TEACHER = new CurrentUser(9901, Set.of("TEACHER"), Set.of("course:manage"), 1);

    @Autowired private CourseService courseService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        dropDelays();
        jdbcTemplate.update("DELETE FROM course_file_delete_journal");
        jdbcTemplate.update("DELETE FROM course_event_outbox");
        jdbcTemplate.update("DELETE FROM course_membership_reconciliation_checkpoint");
        jdbcTemplate.update("DELETE FROM crs_announcement");
        jdbcTemplate.update("DELETE FROM crs_resource");
        CourseTestDataCleanup.deleteChapters(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_course");
    }

    @AfterEach
    void cleanUp() {
        dropDelays();
    }

    @Test
    void onlyOneOfTwoConcurrentSameSiblingOrderCreatesCanSucceed() throws Exception {
        CourseService.CourseView created = courseService.create(
                "issue312-sibling-order", "", "PUBLIC", null, null, TEACHER, UUID.randomUUID().toString());
        long courseId = Long.parseLong(created.id());

        // Widen the pre-check-to-insert window so both transactions observe no
        // sibling before either INSERT; the unique active-order index decides.
        jdbcTemplate.execute("""
                CREATE TRIGGER issue312_chapter_order_delay BEFORE INSERT ON crs_chapter
                FOR EACH ROW DO SLEEP(IF(NEW.sort_order = 7, 1, 0))
                """);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<String> rejection = new AtomicReference<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> chapterOutcome(courseId, ready, start, rejection));
            Future<Boolean> second = pool.submit(() -> chapterOutcome(courseId, ready, start, rejection));
            ready.await();
            start.countDown();
            boolean firstCreated = first.get(15, TimeUnit.SECONDS);
            boolean secondCreated = second.get(15, TimeUnit.SECONDS);

            assertThat(firstCreated == secondCreated).isFalse();
            assertThat(rejection.get()).isEqualTo("CHAPTER_ORDER_CONFLICT");
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM crs_chapter
                 WHERE course_id = ? AND parent_id IS NULL AND sort_order = 7 AND is_deleted = FALSE
                """, Integer.class, courseId)).isEqualTo(1);
    }

    private boolean chapterOutcome(long courseId, CountDownLatch ready, CountDownLatch start,
                                   AtomicReference<String> rejection) {
        ready.countDown();
        try {
            start.await();
            courseService.createChapter(courseId, "concurrent sibling", null, 7, "", true, 1, TEACHER);
            return true;
        } catch (CourseException rejected) {
            rejection.compareAndSet(null, rejected.code());
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void dropDelays() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS issue312_chapter_order_delay");
    }
}
