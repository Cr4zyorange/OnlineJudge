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

/** Runs only in verify-course-service-live.sh against disposable MySQL 8.4. */
@SpringBootTest(classes = CourseServiceApplication.class)
@EnabledIfSystemProperty(named = "course.test.mysql", matches = "true")
class CourseCapacityConcurrencyMySqlTest {
    private static final CurrentUser TEACHER = new CurrentUser(9801, Set.of("TEACHER"), Set.of("course:manage"), 1);

    @Autowired private CourseService courseService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        dropDelays();
        jdbcTemplate.update("DELETE FROM course_event_outbox");
        jdbcTemplate.update("DELETE FROM course_membership_reconciliation_checkpoint");
        jdbcTemplate.update("DELETE FROM crs_announcement");
        jdbcTemplate.update("DELETE FROM crs_resource");
        jdbcTemplate.update("DELETE FROM crs_chapter");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_course");
    }

    @AfterEach
    void cleanUp() {
        dropDelays();
    }

    @Test
    void onlyOneOfTwoConcurrentJoinsCanTakeTheFinalSeat() throws Exception {
        CourseService.CourseView created = courseService.create(
                "issue312-final-seat", "", "PUBLIC", null, 2, TEACHER, UUID.randomUUID().toString());
        long courseId = Long.parseLong(created.id());

        // Widen the check-to-insert window so a non-locking capacity check
        // deterministically lets both transactions observe the free seat.
        jdbcTemplate.execute("""
                CREATE TRIGGER issue312_capacity_join_delay BEFORE INSERT ON crs_course_member
                FOR EACH ROW DO SLEEP(IF(NEW.join_method = 'PUBLIC', 1, 0))
                """);

        CurrentUser firstStudent = new CurrentUser(9802, Set.of("STUDENT"), Set.of(), 1);
        CurrentUser secondStudent = new CurrentUser(9803, Set.of("STUDENT"), Set.of(), 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<String> rejection = new AtomicReference<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> joinOutcome(courseId, firstStudent, ready, start, rejection));
            Future<Boolean> second = pool.submit(() -> joinOutcome(courseId, secondStudent, ready, start, rejection));
            ready.await();
            start.countDown();
            boolean firstJoined = first.get(15, TimeUnit.SECONDS);
            boolean secondJoined = second.get(15, TimeUnit.SECONDS);

            assertThat(firstJoined == secondJoined).isFalse();
            assertThat(rejection.get()).isEqualTo("COURSE_CAPACITY_REACHED");
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM crs_course_member
                 WHERE course_id = ? AND join_status = 'ACTIVE' AND is_deleted = FALSE
                """, Integer.class, courseId)).isEqualTo(2);
    }

    private boolean joinOutcome(long courseId, CurrentUser user, CountDownLatch ready, CountDownLatch start,
                                AtomicReference<String> rejection) throws Exception {
        ready.countDown();
        start.await();
        try {
            courseService.join(courseId, null, user, UUID.randomUUID().toString());
            return true;
        } catch (CourseException rejected) {
            rejection.compareAndSet(null, rejected.code());
            return false;
        }
    }

    private void dropDelays() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS issue312_capacity_join_delay");
    }
}
