package com.onlinejudge.courseservice;

import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.service.CourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs only in verify-course-service-live.sh against disposable MySQL 8.4. */
@SpringBootTest(classes = CourseServiceApplication.class)
@EnabledIfSystemProperty(named = "course.test.mysql", matches = "true")
class CourseGeneratedKeyMySqlConcurrencyTest {
    private static final CurrentUser TEACHER = new CurrentUser(9901, Set.of("TEACHER"), Set.of("course:manage"), 1);

    @Autowired private CourseService courseService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        dropDelays();
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
    void generatedKeysKeepConcurrentCourseMembershipOutboxChapterAndResourceFactsWithTheirRequest() throws Exception {
        jdbcTemplate.execute("""
                CREATE TRIGGER issue312_course_key_delay AFTER INSERT ON crs_course
                FOR EACH ROW DO SLEEP(IF(NEW.course_name = 'issue312-delayed-course', 1, 0))
                """);
        CompletableFuture<CourseService.CourseView> delayedCourse = CompletableFuture.supplyAsync(() ->
                courseService.create("issue312-delayed-course", "", "PUBLIC", null, null, TEACHER, UUID.randomUUID().toString()));
        Thread.sleep(200);
        CompletableFuture<CourseService.CourseView> fastCourse = CompletableFuture.supplyAsync(() ->
                courseService.create("issue312-fast-course", "", "PUBLIC", null, null, TEACHER, UUID.randomUUID().toString()));
        CourseService.CourseView delayed = delayedCourse.get(5, TimeUnit.SECONDS);
        CourseService.CourseView fast = fastCourse.get(5, TimeUnit.SECONDS);

        assertThat(delayed.id()).isNotEqualTo(fast.id());
        for (CourseService.CourseView created : List.of(delayed, fast)) {
            long createdId = Long.parseLong(created.id());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND user_id = ?", Integer.class,
                    createdId, TEACHER.id())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox WHERE aggregate_id IN (?, ?)", Integer.class,
                    createdId + ":" + TEACHER.id(), String.valueOf(createdId))).isEqualTo(2);
        }

        long courseId = Long.parseLong(delayed.id());
        CourseService.ChapterView laterSorted = courseService.createChapter(courseId, "later sorted", null, 99, null, true, null, TEACHER);
        CourseService.ChapterView earlierSorted = courseService.createChapter(courseId, "earlier sorted", null, 1, null, true, null, TEACHER);
        assertThat(earlierSorted.id()).isNotEqualTo(laterSorted.id());
        assertThat(earlierSorted.title()).isEqualTo("earlier sorted");

        jdbcTemplate.execute("""
                CREATE TRIGGER issue312_resource_key_delay AFTER INSERT ON crs_resource
                FOR EACH ROW DO SLEEP(IF(NEW.resource_name = 'issue312-delayed-resource', 1, 0))
                """);
        CompletableFuture<CourseService.ResourceView> delayedResource = CompletableFuture.supplyAsync(() ->
                courseService.createResource(courseId, "issue312-delayed-resource", "https://example.test/delayed", null,
                        "LINK", "STUDENT", null, TEACHER));
        Thread.sleep(200);
        CompletableFuture<CourseService.ResourceView> fastResource = CompletableFuture.supplyAsync(() ->
                courseService.createResource(courseId, "issue312-fast-resource", "https://example.test/fast", null,
                        "LINK", "STUDENT", null, TEACHER));
        CourseService.ResourceView delayedResult = delayedResource.get(5, TimeUnit.SECONDS);
        CourseService.ResourceView fastResult = fastResource.get(5, TimeUnit.SECONDS);
        assertThat(delayedResult.id()).isNotEqualTo(fastResult.id());
        assertThat(List.of(delayedResult.title(), fastResult.title()))
                .containsExactlyInAnyOrder("issue312-delayed-resource", "issue312-fast-resource");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_resource WHERE course_id = ?", Integer.class, courseId)).isEqualTo(2);
    }

    private void dropDelays() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS issue312_course_key_delay");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS issue312_resource_key_delay");
    }
}
