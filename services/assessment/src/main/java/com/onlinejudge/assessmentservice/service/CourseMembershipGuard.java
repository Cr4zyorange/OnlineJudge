package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves LAB student membership before any LAB facts are written. */
@Component
public class CourseMembershipGuard {
    private final CourseMemberProjectionRepository members;
    private final CoursePermissionClient coursePermissions;

    public CourseMembershipGuard(CourseMemberProjectionRepository members, CoursePermissionClient coursePermissions) {
        this.members = members;
        this.coursePermissions = coursePermissions;
    }

    public boolean isActiveMember(String courseId, String userId, String requestId) {
        if (members.isAuthoritativeFor(courseId, userId)) {
            return members.isActive(courseId, userId);
        }
        String effectiveRequestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        return coursePermissions.canViewCourse(courseId, userId, effectiveRequestId);
    }
}
