package com.onlinejudge.assessmentservice.service;

import java.util.UUID;

/** Live Course authorization is the sole authority for management decisions. */
public interface CoursePermissionClient {
    /**
     * Grants management permission for the course. An explicit negative decision returns {@code false};
     * a downstream that cannot produce a decision throws {@link CourseAuthorizationUnavailableException}
     * so callers fail closed with 503 instead of misreporting a denial.
     */
    boolean canManageCourse(String courseId, String userId) throws CourseAuthorizationUnavailableException;

    /** Write commands retain the initiating API correlation across the CRS authorization hop. */
    default boolean canManageCourse(String courseId, String userId, String requestId) {
        return canManageCourse(courseId, userId);
    }

    /** Read access uses CRS VIEW when the local membership projection is incomplete. */
    default boolean canViewCourse(String courseId, String userId) {
        return canViewCourse(courseId, userId, UUID.randomUUID().toString());
    }

    /** Implementations should forward the caller's correlation id to CRS. */
    default boolean canViewCourse(String courseId, String userId, String requestId) {
        return canManageCourse(courseId, userId, requestId);
    }
}
