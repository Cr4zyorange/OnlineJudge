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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the reviewed archive/update race.  update() must take the
 * course-row FOR UPDATE lock before validating lifecycle state, and archive()
 * must serialize on that same lock, so a request carrying status ACTIVE can
 * never observe an ACTIVE snapshot, let an archive commit, and then revive the
 * archived row.
 */
@SpringBootTest(classes = CourseServiceApplication.class)
@EnabledIfSystemProperty(named = "course.test.mysql", matches = "true")
class CourseArchiveUpdateInterleavingMySqlTest {
    private static final CurrentUser TEACHER = new CurrentUser(9701, Set.of("TEACHER"), Set.of("course:manage"), 1);

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
    void archiveCommittingWhileAnUpdateIsInFlightCanNeverBeRevivedByStatusActive() throws Exception {
        CourseService.CourseView created = courseService.create(
                "issue312-archive-update-race", "", "PUBLIC", null, null, TEACHER, UUID.randomUUID().toString());
        long courseId = Long.parseLong(created.id());

        // Widen the archive write window so the interleaving is deterministic:
        // the archive UPDATE holds the course-row lock for one second.  A
        // concurrent update that passes a non-locking ACTIVE read and then
        // blocks on FOR UPDATE would observe the committed ARCHIVED row; it
        // must reject rather than write status ACTIVE over the archive.
        jdbcTemplate.execute("""
                CREATE TRIGGER issue312_archive_update_delay BEFORE UPDATE ON crs_course
                FOR EACH ROW DO SLEEP(IF(NEW.status = 'ARCHIVED', 1, 0))
                """);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch archiveStarted = new CountDownLatch(1);
        Future<String> archive = pool.submit(() -> archiveOutcome(courseId, archiveStarted));
        // Let the archive begin its delayed write, then race the update in so
        // its non-locking reads observe ACTIVE while the archive is uncommitted.
        archiveStarted.await();
        Thread.sleep(200);
        Future<String> update = pool.submit(() -> updateOutcome(courseId));

        assertThat(archive.get(15, TimeUnit.SECONDS)).isNull();
        assertThat(update.get(15, TimeUnit.SECONDS)).isEqualTo("COURSE_READ_ONLY");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM crs_course WHERE id = ?", String.class, courseId))
                .isEqualTo("ARCHIVED");
        pool.shutdownNow();
    }

    @Test
    void updateWithStatusActiveCannotReviveAnAlreadyArchivedCourse() {
        CourseService.CourseView created = courseService.create(
                "issue312-archived-read-only", "", "PUBLIC", null, null, TEACHER, UUID.randomUUID().toString());
        long courseId = Long.parseLong(created.id());
        courseService.archive(courseId, TEACHER);

        assertThat(updateOutcome(courseId)).isEqualTo("COURSE_READ_ONLY");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM crs_course WHERE id = ?", String.class, courseId))
                .isEqualTo("ARCHIVED");
    }

    private String archiveOutcome(long courseId, CountDownLatch started) {
        started.countDown();
        try {
            courseService.archive(courseId, TEACHER);
            return null;
        } catch (CourseException rejected) {
            return rejected.code();
        }
    }

    private String updateOutcome(long courseId) {
        try {
            courseService.update(courseId, null, null, null, null, null, "ACTIVE", TEACHER);
            return null;
        } catch (CourseException rejected) {
            return rejected.code();
        }
    }

    private void dropDelays() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS issue312_archive_update_delay");
    }
}
