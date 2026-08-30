package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.service.AssessmentSubmissionService;
import com.onlinejudge.assessmentservice.service.CourseMembershipProjectionService;
import com.onlinejudge.assessmentservice.worker.AssessmentWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;

/** v2 producer and consumer ordering facts, all persisted in the Assessment schema. */
@SpringBootTest
class WorkerAndProjectionReliabilityTest {
    @Autowired AssessmentSubmissionService submissions;
    @Autowired AssessmentWorker worker;
    @Autowired EvaluationTaskRepository tasks;
    @Autowired CourseMembershipProjectionService members;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_outbox");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_event_inbox");
        jdbc.update("DELETE FROM assessment_course_projection_gap");
        jdbc.update("DELETE FROM assessment_course_member_projection");
    }

    @Test
    void workerCompletionWritesEvaluationAndCanonicalSourceGradeOutboxInOneAssessmentTransaction() {
        var submitted = submissions.submit(new AssessmentSubmissionService.SubmissionCommand("HWK", "homework-1", "course-1", "student-1", "persistent://one"));
        worker.runOne("worker-a", task -> AssessmentWorker.EvaluationOutcome.successful("ACCEPTED"));

        assertThat(tasks.find(submitted.taskId()).orElseThrow().state().name()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type = 'HWK' AND source_id = 'homework-1' AND student_id = 'student-1'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.evaluation.completed.v2'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_outbox WHERE event_type = 'assessment.source-grade.changed.v2'", Integer.class)).isEqualTo(1);
    }

    @Test
    void outboxPersistsTheCanonicalV2EnvelopeRatherThanAnUnaddressablePayloadFragment() throws Exception {
        var submitted = submissions.submit(new AssessmentSubmissionService.SubmissionCommand("HWK", "homework-envelope", "course-1", "student-1", "persistent://one"));
        worker.runOne("worker-envelope", task -> AssessmentWorker.EvaluationOutcome.successful("ACCEPTED"));
        String json = jdbc.queryForObject("SELECT payload_json FROM assessment_event_outbox WHERE correlation_id = ? AND event_type = 'assessment.source-grade.changed.v2'", String.class, submitted.taskId());
        var root = new ObjectMapper().readTree(json);
        assertThat(root.path("eventType").asText()).isEqualTo("assessment.source-grade.changed.v2");
        assertThat(root.path("eventId").asText()).isNotBlank();
        assertThat(root.path("payload").path("sourceId").asText()).isEqualTo("homework-envelope");
    }

    @Test
    void courseProjectionDeduplicatesAndDefersOutOfOrderVersionUntilGapIsClosed() {
        var late = members.apply(new CourseMembershipProjectionService.MemberChanged("event-2", "course-1", "student-1", "ACTIVE", 2));
        assertThat(late.decision()).isEqualTo("GAP");
        assertThat(members.apply(new CourseMembershipProjectionService.MemberChanged("event-1", "course-1", "student-1", "ACTIVE", 1)).decision()).isEqualTo("APPLIED");
        assertThat(members.apply(new CourseMembershipProjectionService.MemberChanged("event-2", "course-1", "student-1", "ACTIVE", 2)).decision()).isEqualTo("APPLIED");
        assertThat(members.apply(new CourseMembershipProjectionService.MemberChanged("event-2", "course-1", "student-1", "ACTIVE", 2)).decision()).isEqualTo("DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT member_version FROM assessment_course_member_projection WHERE course_id = 'course-1' AND user_id = 'student-1'", Long.class)).isEqualTo(2L);
    }
}
