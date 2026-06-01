package com.onlinejudge.integration.course;

import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.mapper.CourseRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CourseRepository courseRepository;

    public HeaderCoursePermissionClient(@Value("${onlinejudge.auth.allow-header-auth:false}") boolean allowHeaderAuth) {
        this(allowHeaderAuth, null);
    }

    @Autowired
    public HeaderCoursePermissionClient(
            @Value("${onlinejudge.auth.allow-header-auth:false}") boolean allowHeaderAuth,
            CourseRepository courseRepository
    ) {
        this.allowHeaderAuth = allowHeaderAuth;
        this.courseRepository = courseRepository;
    }

    @Override
    public boolean canManageCourse(long courseId, long userId) {
        if (userId <= 0 || courseId <= 0) {
            return false;
        }
        if (allowHeaderAuth && isAdmin()) {
            return true;
        }
        if (allowHeaderAuth && hasCourseHeader("X-Manageable-Course-Ids", courseId)) {
            return true;
        }
        return canManageFromRepository(courseId, userId);
    }

    @Override
    public boolean canViewCourse(long courseId, long userId) {
        if (userId <= 0 || courseId <= 0) {
            return false;
        }
        return canManageCourse(courseId, userId)
                || (allowHeaderAuth && hasCourseHeader("X-Course-Ids", courseId))
                || canViewFromRepository(courseId, userId);
    }

    @Override
    public boolean isCourseMember(long courseId, long userId) {
        return canViewCourse(courseId, userId);
    }

    @Override
    public List<Long> listCourseStudentIds(long courseId) {
        if (courseId <= 0) {
            return List.of();
        }
        if (allowHeaderAuth) {
            HttpServletRequest request = currentRequest();
            if (request != null) {
                String roster = request.getHeader("X-Course-Student-Ids");
                if (roster != null && !roster.isBlank()) {
                    return Arrays.stream(roster.split(";"))
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .filter(value -> value.startsWith(courseId + ":"))
                            .findFirst()
                            .map(value -> parseStudentIds(value.substring(value.indexOf(':') + 1)))
                            .orElseGet(() -> roster.contains(":") ? List.of() : parseStudentIds(roster));
                }
            }
        }
        return courseRepository == null ? List.of() : courseRepository.listActiveStudentIds(courseId);
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

    private boolean canManageFromRepository(long courseId, long userId) {
        return activeMember(courseId, userId)
                .map(CourseMember::role)
                .filter(role -> role == CourseMemberRole.TEACHER || role == CourseMemberRole.ASSISTANT)
                .isPresent();
    }

    private boolean canViewFromRepository(long courseId, long userId) {
        return activeMember(courseId, userId).isPresent();
    }

    private java.util.Optional<CourseMember> activeMember(long courseId, long userId) {
        if (courseRepository == null) {
            return java.util.Optional.empty();
        }
        return courseRepository.findMember(courseId, userId)
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
}
