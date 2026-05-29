package com.onlinejudge.integration.course;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class HeaderCoursePermissionClient implements CoursePermissionClient {
    private final boolean allowHeaderAuth;

    public HeaderCoursePermissionClient(@Value("${onlinejudge.auth.allow-header-auth:false}") boolean allowHeaderAuth) {
        this.allowHeaderAuth = allowHeaderAuth;
    }

    @Override
    public boolean canManageCourse(long courseId, long userId) {
        if (!allowHeaderAuth) {
            return false;
        }
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
        if (!allowHeaderAuth) {
            return false;
        }
        if (userId <= 0 || courseId <= 0) {
            return false;
        }
        return canManageCourse(courseId, userId) || hasCourseHeader("X-Course-Ids", courseId);
    }

    @Override
    public boolean isCourseMember(long courseId, long userId) {
        return canViewCourse(courseId, userId);
    }

    @Override
    public List<Long> listCourseStudentIds(long courseId) {
        if (!allowHeaderAuth) {
            return List.of();
        }
        if (courseId <= 0) {
            return List.of();
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return List.of();
        }
        String roster = request.getHeader("X-Course-Student-Ids");
        if (roster == null || roster.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roster.split(";"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> value.startsWith(courseId + ":"))
                .findFirst()
                .map(value -> parseStudentIds(value.substring(value.indexOf(':') + 1)))
                .orElseGet(() -> roster.contains(":") ? List.of() : parseStudentIds(roster));
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

    private List<Long> parseStudentIds(String value) {
        List<Long> studentIds = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                studentIds.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        return List.copyOf(new LinkedHashSet<>(studentIds));
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
}
