package com.onlinejudge.gradeservice;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "grade.rabbit.enabled=false")
class GradeOutboxNotificationEventPublisherTest {
    @Autowired NotificationEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM grade_event_outbox");
    }

    @Test
    void publicationFactIsDurableAndBusinessIdempotent() {
        NotificationEvent event = publication("GRD:GRADE_PUBLISHED:PUBLISH:901");

        publisher.publish(event);
        publisher.publish(event);

        assertThat(outboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM grade_event_outbox", String.class))
                .isEqualTo("grade.published.v2");
        assertThat(jdbc.queryForObject("SELECT payload_json FROM grade_event_outbox", String.class))
                .contains("\"publicationId\":\"901\"")
                .contains("\"publicationVersion\":1")
                .doesNotContain("receiverStudentIds");
    }

    @Test
    void courseGradeAdjustmentPublishesTheUpdatedSummaryAsAVisibilityFact() {
        publisher.publish(new NotificationEvent("GRD:GRADE_CHANGED:COURSE_TOTAL:904", "GRADE_CHANGED",
                101, List.of(601L), "成绩已调整", "课程总评已调整", "COURSE_GRADE_SUMMARY", 904L,
                "/courses/101/grades/904", LocalDateTime.of(2026, 9, 3, 9, 0)));

        assertThat(outboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM grade_event_outbox", String.class))
                .isEqualTo("grade.published.v2");
        assertThat(jdbc.queryForObject("SELECT aggregate_type FROM grade_event_outbox", String.class))
                .isEqualTo("course-grade-summary");
        assertThat(jdbc.queryForObject("SELECT payload_json FROM grade_event_outbox", String.class))
                .contains("\"publicationId\":\"904\"")
                .contains("\"courseId\":\"101\"");
    }

    @Test
    void aRolledBackGradeTransactionLeavesNoOutboxFact() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            publisher.publish(publication("GRD:GRADE_PUBLISHED:PUBLISH:902"));
            throw new IllegalStateException("rollback grade fact");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxCount()).isZero();
    }

    @Test
    void processedReviewUsesTheFrozenClosedPayload() {
        jdbc.update("""
                INSERT INTO t_grade_review_request
                    (id,course_id,student_id,target_type,reason,status,submitted_at,processed_by,processed_at,created_at,updated_at)
                VALUES (903,101,601,'COURSE_TOTAL','check','APPROVED',CURRENT_TIMESTAMP,7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        publisher.publish(new NotificationEvent("GRD:GRADE_REVIEW_PROCESSED:REQUEST:903", "GRADE_REVIEW_PROCESSED",
                101, List.of(601L), "processed", "processed", "GRADE_REVIEW_REQUEST", 903L,
                "/courses/101/grades/reviews/903", LocalDateTime.of(2026, 9, 1, 8, 0)));

        assertThat(jdbc.queryForObject("SELECT payload_json FROM grade_event_outbox", String.class))
                .contains("\"eventType\":\"grade.review.processed.v2\"")
                .contains("\"studentId\":\"601\"")
                .contains("\"reviewStatus\":\"APPROVED\"")
                .contains("\"resultVersion\":1");
    }

    private NotificationEvent publication(String key) {
        return new NotificationEvent(key, "GRADE_PUBLISHED", 101, List.of(601L, 602L),
                "成绩已发布", "课程成绩已发布", "GRADE_PUBLISH_RECORD", 901L,
                "/courses/101?page=grades", LocalDateTime.of(2026, 9, 1, 8, 0));
    }

    private int outboxCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM grade_event_outbox", Integer.class);
    }
}
