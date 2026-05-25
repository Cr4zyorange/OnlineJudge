package com.onlinejudge.integration.course;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Component
public class HeaderCoursePermissionClient implements CoursePermissionClient {
    @Override
    public boolean canManageCourse(long courseId, long userId) {
        if (userId <= 0 || courseId <= 0) {
            return false;
        }
        if (isAdmin()) {
            return true;
        }
        return hasCourseHeader("X-Manageable-Course-Ids", courseId);
    }

    @Override
    public boolean canViewCourse(long courseId, long userId) {
        if (userId <= 0 || courseId <= 0) {
            return false;
        }
        return canManageCourse(courseId, userId) || hasCourseHeader("X-Course-Ids", courseId);
    }

    @Override
    public boolean isCourseMember(long courseId, long userId) {
        return canViewCourse(courseId, userId);
    }

    private boolean isAdmin() {
        HttpServletRequest request = currentRequest();
        return request != null && "ADMIN".equals(request.getHeader("X-User-Role"));
    }

    private boolean hasCourseHeader(String headerName, long courseId) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String courseIds = request.getHeader(headerName);
        if (courseIds == null || courseIds.isBlank()) {
            return false;
        }
        return Arrays.stream(courseIds.split(","))
                .map(String::trim)
                .anyMatch(value -> "*".equals(value) || Long.toString(courseId).equals(value));
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
}
