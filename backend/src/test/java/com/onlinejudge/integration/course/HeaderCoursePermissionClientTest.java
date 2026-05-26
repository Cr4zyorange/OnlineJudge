package com.onlinejudge.integration.course;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient();

        assertThat(client.isCourseMember(101L, 501L)).isTrue();
        assertThat(client.canViewCourse(101L, 501L)).isTrue();
        assertThat(client.canManageCourse(101L, 501L)).isFalse();
        assertThat(client.canManageCourse(202L, 501L)).isTrue();
        assertThat(client.canManageCourseGrade(202L, 501L)).isTrue();
        assertThat(client.canViewCourse(303L, 501L)).isFalse();
    }

    @Test
    void grantsAllCoursePermissionsToAdminWildcard() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-Course-Ids", "*");
        request.addHeader("X-Manageable-Course-Ids", "*");
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient();

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

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient();

        assertThat(client.canViewCourse(0L, 501L)).isFalse();
        assertThat(client.canManageCourse(101L, 0L)).isFalse();
    }

    @Test
    void readsCourseStudentRosterFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Course-Student-Ids", "101:601,602,603;202:701");
        bind(request);

        HeaderCoursePermissionClient client = new HeaderCoursePermissionClient();

        assertThat(client.listCourseStudentIds(101L)).containsExactly(601L, 602L, 603L);
        assertThat(client.listCourseStudentIds(202L)).containsExactly(701L);
        assertThat(client.listCourseStudentIds(303L)).isEmpty();
    }

    private void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
