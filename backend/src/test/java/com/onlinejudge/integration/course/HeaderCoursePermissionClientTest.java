package com.onlinejudge.integration.course;

import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.mapper.CourseRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderCoursePermissionClientTest {
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void grantsCourseMemberReadAndTeacherManagePermissionsFromHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "TEACHER");
        request.addHeader("X-Course-Ids", "101, 202");
        request.addHeader("X-Manageable-Course-Ids", "202");
        request.addHeader("X-Course-Student-Ids", "7001, 7002, 7002");
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient(true);

        assertThat(client.isCourseMember(101L, 501L)).isTrue();
        assertThat(client.canViewCourse(101L, 501L)).isTrue();
        assertThat(client.canManageCourse(101L, 501L)).isFalse();
        assertThat(client.canManageCourse(202L, 501L)).isTrue();
        assertThat(client.canManageCourseGrade(202L, 501L)).isTrue();
        assertThat(client.canViewCourse(303L, 501L)).isFalse();
        assertThat(client.listCourseStudentIds(101L)).containsExactly(7001L, 7002L);
    }

    @Test
    void grantsAllCoursePermissionsToAdminWildcard() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-Course-Ids", "*");
        request.addHeader("X-Manageable-Course-Ids", "*");
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient(true);

        assertThat(client.isCourseMember(999L, 1L)).isTrue();
        assertThat(client.canManageCourse(999L, 1L)).isTrue();
        assertThat(client.canManageCourseGrade(999L, 1L)).isTrue();
    }

    @Test
    void deniesInvalidCourseOrUserIdentifiers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "TEACHER");
        request.addHeader("X-Course-Ids", "*");
        request.addHeader("X-Manageable-Course-Ids", "*");
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient(true);

        assertThat(client.canViewCourse(0L, 501L)).isFalse();
        assertThat(client.canManageCourse(101L, 0L)).isFalse();
    }

    @Test
    void readsCourseStudentRosterFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Course-Student-Ids", "101:601,602,603;202:701");
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient(true);

        assertThat(client.listCourseStudentIds(101L)).containsExactly(601L, 602L, 603L);
        assertThat(client.listCourseStudentIds(202L)).containsExactly(701L);
        assertThat(client.listCourseStudentIds(303L)).isEmpty();
    }

    @Test
    void ignoresHeaderCoursePermissionsWhenHeaderAuthIsDisabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-Course-Ids", "*");
        request.addHeader("X-Manageable-Course-Ids", "*");
        request.addHeader("X-Course-Student-Ids", "101:601");
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient(false);

        assertThat(client.canViewCourse(101L, 501L)).isFalse();
        assertThat(client.canManageCourse(101L, 501L)).isFalse();
        assertThat(client.listCourseStudentIds(101L)).isEmpty();
    }

    @Test
    void fallsBackToCrsMembershipWhenHeaderAuthIsDisabled() {
        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient(false, new StubCourseRepository(
                List.of(
                        member(101L, 501L, CourseMemberRole.TEACHER, CourseMemberStatus.ACTIVE),
                        member(101L, 601L, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE),
                        member(101L, 602L, CourseMemberRole.STUDENT, CourseMemberStatus.REMOVED),
                        member(202L, 501L, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE)
                )
        ));

        assertThat(client.canManageCourse(101L, 501L)).isTrue();
        assertThat(client.canViewCourse(101L, 601L)).isTrue();
        assertThat(client.canManageCourse(202L, 501L)).isFalse();
        assertThat(client.canViewCourse(101L, 602L)).isFalse();
        assertThat(client.listCourseStudentIds(101L)).containsExactly(601L);
    }

    private void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private CourseMember member(long courseId, long userId, CourseMemberRole role, CourseMemberStatus status) {
        return new CourseMember(1L, courseId, userId, role, "TEST", status, 501L, LocalDateTime.now());
    }

    private static final class StubCourseRepository extends CourseRepository {
        private final List<CourseMember> members;

        private StubCourseRepository(List<CourseMember> members) {
            super(null);
            this.members = members;
        }

        @Override
        public Optional<CourseMember> findMember(Long courseId, Long userId) {
            return members.stream()
                    .filter(member -> member.courseId().equals(courseId) && member.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<Long> listActiveStudentIds(Long courseId) {
            return members.stream()
                    .filter(member -> member.courseId().equals(courseId))
                    .filter(member -> member.role() == CourseMemberRole.STUDENT)
                    .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                    .map(CourseMember::userId)
                    .toList();
        }
    }
}
