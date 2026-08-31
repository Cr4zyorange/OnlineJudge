package com.onlinejudge.assessmentservice.service;

/** Live Course authorization is the sole authority for management decisions. */
public interface CoursePermissionClient {
    boolean canManageCourse(String courseId, String userId);
}
