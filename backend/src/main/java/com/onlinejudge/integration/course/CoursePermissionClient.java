package com.onlinejudge.integration.course;

@FunctionalInterface
public interface CoursePermissionClient {
    boolean canManageCourse(long courseId, long userId);

    default boolean canViewCourse(long courseId, long userId) {
        return canManageCourse(courseId, userId);
    }

    default boolean isCourseMember(long courseId, long userId) {
        return canViewCourse(courseId, userId);
    }

    default boolean canManageCourseGrade(long courseId, long userId) {
        return canManageCourse(courseId, userId);
    }
}
