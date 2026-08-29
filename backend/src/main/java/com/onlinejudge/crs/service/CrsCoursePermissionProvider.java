package com.onlinejudge.crs.service;

import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.mapper.CourseRepository;
import com.onlinejudge.integration.course.CoursePermissionProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * #310 C-03 课程权限生产者：CRS 在自己拥有的课程成员数据之上实现
 * {@link CoursePermissionProvider}，消费方只通过契约访问，不触碰 CRS 内部表。
 */
@Component
public class CrsCoursePermissionProvider implements CoursePermissionProvider {
    private final CourseRepository courseRepository;

    public CrsCoursePermissionProvider(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public boolean courseExists(long courseId) {
        return courseId > 0 && courseRepository.findById(courseId).isPresent();
    }

    @Override
    public boolean canManageCourse(long courseId, long userId) {
        if (courseId <= 0 || userId <= 0) {
            return false;
        }
        return activeMember(courseId, userId)
                .map(CourseMember::role)
                .filter(role -> role == CourseMemberRole.TEACHER || role == CourseMemberRole.ASSISTANT)
                .isPresent();
    }

    @Override
    public boolean canViewCourse(long courseId, long userId) {
        if (courseId <= 0 || userId <= 0) {
            return false;
        }
        return activeMember(courseId, userId).isPresent();
    }

    @Override
    public List<Long> listActiveStudentIds(long courseId) {
        return courseId <= 0 ? List.of() : courseRepository.listActiveStudentIds(courseId);
    }

    @Override
    public List<Long> listActiveTeacherIds(long courseId) {
        return courseId <= 0 ? List.of() : courseRepository.listActiveTeacherIds(courseId);
    }

    private Optional<CourseMember> activeMember(long courseId, long userId) {
        return courseRepository.findMember(courseId, userId)
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE);
    }
}
