package com.onlinejudge.integration.course;

import java.util.List;

@FunctionalInterface
public interface CoursePermissionClient {
    /**
     * #310 C-03 契约版本。任何破坏性变更必须先发布 v2 并保留 v1 兼容期。
     */
    String VERSION = "v1";

    boolean canManageCourse(long courseId, long userId);

    default boolean courseExists(long courseId) {
        return false;
    }

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
