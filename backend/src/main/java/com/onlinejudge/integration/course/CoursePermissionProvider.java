package com.onlinejudge.integration.course;

import java.util.List;

/**
 * #310 C-03 课程权限生产者 SPI（v1）。
 * <p>由 CRS 服务实现并在其拥有数据之上提供课程存在性、成员关系、管理权限与名单；
 * 消费方只允许通过 {@link CoursePermissionClient} 访问，不得读取 CRS 内部表或实现。</p>
 */
public interface CoursePermissionProvider {

    boolean courseExists(long courseId);

    boolean canManageCourse(long courseId, long userId);

    boolean canViewCourse(long courseId, long userId);

    List<Long> listActiveStudentIds(long courseId);

    List<Long> listActiveTeacherIds(long courseId);
}
