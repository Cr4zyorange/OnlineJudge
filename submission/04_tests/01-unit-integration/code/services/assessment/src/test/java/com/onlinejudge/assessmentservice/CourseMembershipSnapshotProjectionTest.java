package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.service.CourseMembershipProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CourseMembershipSnapshotProjectionTest {
    @Autowired CourseMembershipProjectionService projection;
    @Autowired CourseMemberProjectionRepository members;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_event_inbox");
        jdbc.update("DELETE FROM assessment_deferred_course_member_event");
        jdbc.update("DELETE FROM assessment_course_projection_gap");
        jdbc.update("DELETE FROM assessment_course_membership_watermark");
        jdbc.update("DELETE FROM assessment_course_member_projection");
    }

    @Test
    void completeSnapshotBootstrapsAnOtherwiseEmptyPreexistingCourseAndAdvancesItsWatermarkAtomically() {
        var snapshot = new CourseMembershipProjectionService.RosterSnapshot("snapshot-7", "course-preexisting", 7, List.of(
                new CourseMembershipProjectionService.RosterMember("student-1", "ACTIVE", 3),
                new CourseMembershipProjectionService.RosterMember("former-student", "REMOVED", 4)));

        assertThat(projection.applySnapshot(snapshot).decision()).isEqualTo("APPLIED");
        assertThat(members.isAuthoritativeFor("course-preexisting", "student-1")).isTrue();
        assertThat(members.isActive("course-preexisting", "student-1")).isTrue();
        assertThat(members.isActive("course-preexisting", "former-student")).isFalse();
        assertThat(members.isAuthoritativeFor("course-preexisting", "never-enrolled")).isTrue();
        assertThat(members.isActive("course-preexisting", "never-enrolled")).isFalse();
        assertThat(jdbc.queryForObject("SELECT roster_version FROM assessment_course_membership_watermark WHERE course_id='course-preexisting'", Long.class)).isEqualTo(7L);
        assertThat(projection.applySnapshot(snapshot).decision()).isEqualTo("DUPLICATE");
        assertThat(projection.applySnapshot(new CourseMembershipProjectionService.RosterSnapshot("snapshot-6", "course-preexisting", 6, List.of())).decision()).isEqualTo("STALE");
    }

    @Test
    void authoritativeSnapshotFastForwardsADeferredMemberGapAndDrainsTheSubsequentMemberFact() {
        assertThat(projection.apply(new CourseMembershipProjectionService.MemberChanged("member-4", "course-gap", "student-gap", "REMOVED", 4)).decision()).isEqualTo("GAP");

        assertThat(projection.applySnapshot(new CourseMembershipProjectionService.RosterSnapshot("snapshot-gap", "course-gap", 9,
                List.of(new CourseMembershipProjectionService.RosterMember("student-gap", "ACTIVE", 3)))).decision()).isEqualTo("APPLIED");

        assertThat(members.isActive("course-gap", "student-gap")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_deferred_course_member_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT roster_version FROM assessment_course_membership_watermark WHERE course_id='course-gap'", Long.class)).isEqualTo(9L);
    }
}
