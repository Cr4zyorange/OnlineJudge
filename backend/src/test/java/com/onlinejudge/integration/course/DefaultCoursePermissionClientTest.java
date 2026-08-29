package com.onlinejudge.integration.course;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCoursePermissionClientTest {
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

        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(true, denyingProvider());

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

        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(true, denyingProvider());

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

        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(true, denyingProvider());

        assertThat(client.canViewCourse(0L, 501L)).isFalse();
        assertThat(client.canManageCourse(101L, 0L)).isFalse();
    }

    @Test
    void readsCourseStudentRosterFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Course-Student-Ids", "101:601,602,603;202:701");
        bind(request);

        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(true, denyingProvider());

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

        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(false, denyingProvider());

        assertThat(client.canViewCourse(101L, 501L)).isFalse();
        assertThat(client.canManageCourse(101L, 501L)).isFalse();
        assertThat(client.listCourseStudentIds(101L)).isEmpty();
    }

    @Test
    void delegatesToProducerProviderWhenHeaderAuthIsDisabled() {
        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(false, provider(
                List.of(601L, 602L), List.of(501L)
        ));

        assertThat(client.courseExists(101L)).isTrue();
        assertThat(client.canManageCourse(101L, 501L)).isTrue();
        assertThat(client.canViewCourse(101L, 601L)).isTrue();
        assertThat(client.canManageCourse(202L, 501L)).isFalse();
        assertThat(client.listCourseStudentIds(101L)).containsExactly(601L, 602L);
        assertThat(client.listCourseTeacherIds(101L)).containsExactly(501L);
    }

    private void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static CoursePermissionProvider denyingProvider() {
        return new CoursePermissionProvider() {
            @Override
            public boolean courseExists(long courseId) {
                return false;
            }

            @Override
            public boolean canManageCourse(long courseId, long userId) {
                return false;
            }

            @Override
            public boolean canViewCourse(long courseId, long userId) {
                return false;
            }

            @Override
            public List<Long> listActiveStudentIds(long courseId) {
                return List.of();
            }

            @Override
            public List<Long> listActiveTeacherIds(long courseId) {
                return List.of();
            }
        };
    }

    private static CoursePermissionProvider provider(List<Long> students, List<Long> teachers) {
        return new CoursePermissionProvider() {
            @Override
            public boolean courseExists(long courseId) {
                return courseId == 101L;
            }

            @Override
            public boolean canManageCourse(long courseId, long userId) {
                return courseId == 101L && userId == 501L;
            }

            @Override
            public boolean canViewCourse(long courseId, long userId) {
                return courseId == 101L;
            }

            @Override
            public List<Long> listActiveStudentIds(long courseId) {
                return courseId == 101L ? students : List.of();
            }

            @Override
            public List<Long> listActiveTeacherIds(long courseId) {
                return courseId == 101L ? teachers : List.of();
            }
        };
    }
}
