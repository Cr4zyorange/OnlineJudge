package com.onlinejudge.assessmentservice.service;

/** Live Course authorization is the sole authority for management decisions. */
public interface CoursePermissionClient {
    /**
     * Grants management permission for the course. An explicit negative decision returns {@code false};
     * a downstream that cannot produce a decision throws {@link CourseAuthorizationUnavailableException}
     * so callers fail closed with 503 instead of misreporting a denial.
     */
    boolean canManageCourse(String courseId, String userId) throws CourseAuthorizationUnavailableException;
}
