package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseMembershipGuardTest {
    @Test
    void authoritativeProjectionUsesTheLocalActiveMemberRow() {
        RecordingCoursePermissionClient coursePermissions = new RecordingCoursePermissionClient(true);
        CourseMembershipGuard guard = new CourseMembershipGuard(new StubCourseMemberProjectionRepository(true, true), coursePermissions);

        assertThat(guard.isActiveMember("course-357", "student-357", "request-357")).isTrue();
        assertThat(coursePermissions.requestedCourseId).isNull();
    }

    @Test
    void incompleteProjectionDelegatesToCourseViewWithTheCallerRequestId() {
        RecordingCoursePermissionClient coursePermissions = new RecordingCoursePermissionClient(true);
        CourseMembershipGuard guard = new CourseMembershipGuard(new StubCourseMemberProjectionRepository(false, true), coursePermissions);

        assertThat(guard.isActiveMember("course-357", "student-357", "lab-357-submit")).isTrue();
        assertThat(coursePermissions.requestedCourseId).isEqualTo("course-357");
        assertThat(coursePermissions.requestedUserId).isEqualTo("student-357");
        assertThat(coursePermissions.requestedRequestId).isEqualTo("lab-357-submit");
    }

    @Test
    void blankRequestIdsAreReplacedBeforeDelegatingToCourseView() {
        RecordingCoursePermissionClient coursePermissions = new RecordingCoursePermissionClient(true);
        CourseMembershipGuard guard = new CourseMembershipGuard(new StubCourseMemberProjectionRepository(false, true), coursePermissions);

        assertThat(guard.isActiveMember("course-357", "student-357", "  ")).isTrue();
        assertThat(coursePermissions.requestedRequestId).isNotBlank();
        assertThat(coursePermissions.requestedRequestId).isNotEqualTo("  ");
    }

    private static final class StubCourseMemberProjectionRepository extends CourseMemberProjectionRepository {
        private final boolean authoritative;
        private final boolean active;

        StubCourseMemberProjectionRepository(boolean authoritative, boolean active) {
            super(null);
            this.authoritative = authoritative;
            this.active = active;
        }

        @Override
        public boolean isAuthoritativeFor(String courseId, String userId) {
            return authoritative;
        }

        @Override
        public boolean isActive(String courseId, String userId) {
            return active;
        }
    }

    private static final class RecordingCoursePermissionClient implements CoursePermissionClient {
        private final boolean allowed;
        private String requestedCourseId;
        private String requestedUserId;
        private String requestedRequestId;

        RecordingCoursePermissionClient(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean canManageCourse(String courseId, String userId) {
            throw new AssertionError("manage authorization should not be used here");
        }

        @Override
        public boolean canManageCourse(String courseId, String userId, String requestId) {
            throw new AssertionError("manage authorization should not be used here");
        }

        @Override
        public boolean canViewCourse(String courseId, String userId, String requestId) {
            this.requestedCourseId = courseId;
            this.requestedUserId = userId;
            this.requestedRequestId = requestId;
            return allowed;
        }
    }
}
