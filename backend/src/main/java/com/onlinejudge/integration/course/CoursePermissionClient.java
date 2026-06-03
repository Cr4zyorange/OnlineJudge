package com.onlinejudge.integration.course;

import java.util.List;

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

    default List<Long> listCourseStudentIds(long courseId) {
        return List.of();
    }

    default List<Long> listCourseTeacherIds(long courseId) {
        return List.of();
    }
}
