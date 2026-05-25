package com.onlinejudge.integration.course;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Component
public class HeaderCoursePermissionClient implements CoursePermissionClient {
    @Override
    public boolean canManageCourseGrade(long courseId, long userId) {
        if (userId <= 0 || courseId <= 0) {
            return false;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        String userRole = request.getHeader("X-User-Role");
        if ("ADMIN".equals(userRole)) {
            return true;
        }
        String manageableCourseIds = request.getHeader("X-Manageable-Course-Ids");
        if (manageableCourseIds == null || manageableCourseIds.isBlank()) {
            return false;
        }
        return Arrays.stream(manageableCourseIds.split(","))
                .map(String::trim)
                .anyMatch(value -> "*".equals(value) || Long.toString(courseId).equals(value));
    }
}
