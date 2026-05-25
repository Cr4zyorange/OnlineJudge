package com.onlinejudge.crs.service;

import com.onlinejudge.common.exception.BusinessException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.crs.domain.*;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.domain.dto.CoursePermissionResponse;
import com.onlinejudge.crs.domain.dto.CourseResponse;
import com.onlinejudge.crs.domain.dto.CourseUpdateRequest;
import com.onlinejudge.crs.mapper.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {
    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CourseResponse create(CourseCreateRequest request, CurrentUser user) {
        requireTeacher(user);
        Course course = courseRepository.insert(request, user.id());
        courseRepository.insertMember(course.id(), user.id(), CourseMemberRole.TEACHER, CourseMemberStatus.ACTIVE, "CREATED", user.id());
        return toResponse(course, user);
    }

    public PageResponse<CourseResponse> list(String keyword, String scope, int page, int size, CurrentUser user) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedScope = scope == null || scope.isBlank() ? "all" : scope;
        return new PageResponse<>(
                courseRepository.list(keyword, normalizedPage, normalizedSize, normalizedScope, user.id(), user.isAdmin()).stream()
                        .map(course -> toResponse(course, user))
                        .toList(),
                courseRepository.count(keyword, normalizedScope, user.id(), user.isAdmin()),
                normalizedPage,
                normalizedSize
        );
    }

    public CourseResponse detail(Long courseId, CurrentUser user) {
        Course course = getCourse(courseId);
        if (!user.isAdmin() && !isActiveMember(courseId, user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
        }
        return toResponse(course, user);
    }

    @Transactional
    public CourseResponse update(Long courseId, CourseUpdateRequest request, CurrentUser user) {
        requireManagePermission(courseId, user);
        return toResponse(courseRepository.update(courseId, request), user);
    }

    @Transactional
    public void archive(Long courseId, CurrentUser user) {
        requireManagePermission(courseId, user);
        courseRepository.archive(courseId);
    }

    @Transactional
    public CoursePermissionResponse join(Long courseId, CurrentUser user) {
        Course course = getCourse(courseId);
        if (course.status() == CourseStatus.ARCHIVED || course.status() == CourseStatus.CLOSED) {
            throw new BusinessException(HttpStatus.CONFLICT, "课程已关闭");
        }
        if (courseRepository.findMember(courseId, user.id()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户已是课程成员");
        }
        CourseMemberStatus status = course.enrollmentMode() == EnrollmentMode.REVIEW ? CourseMemberStatus.PENDING : CourseMemberStatus.ACTIVE;
        CourseMember member = courseRepository.insertMember(courseId, user.id(), CourseMemberRole.STUDENT, status, course.enrollmentMode().name(), null);
        return toPermission(courseId, user.id(), member);
    }

    public CoursePermissionResponse permission(Long courseId, Long userId) {
        getCourse(courseId);
        return toPermission(courseId, userId, courseRepository.findMember(courseId, userId).orElse(null));
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "课程不存在"));
    }

    private void requireTeacher(CurrentUser user) {
        if (!user.isTeacher()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
        }
    }

    private void requireManagePermission(Long courseId, CurrentUser user) {
        getCourse(courseId);
        if (user.isAdmin()) {
            return;
        }
        CourseMember member = courseRepository.findMember(courseId, user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "无权限访问"));
        if (member.status() != CourseMemberStatus.ACTIVE || member.role() != CourseMemberRole.TEACHER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
        }
    }

    private boolean isActiveMember(Long courseId, Long userId) {
        return courseRepository.findMember(courseId, userId)
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                .isPresent();
    }

    private CourseResponse toResponse(Course course, CurrentUser viewer) {
        return new CourseResponse(
                course.id(),
                course.name(),
                course.description(),
                course.teacherId(),
                "教师" + course.teacherId(),
                course.semester(),
                course.category(),
                course.coverUrl(),
                course.enrollmentMode(),
                course.inviteCode(),
                course.maxStudents(),
                course.startDate(),
                course.endDate(),
                course.status(),
                courseRepository.memberCount(course.id()),
                viewer.isAdmin() || courseRepository.findMember(course.id(), viewer.id())
                        .filter(member -> member.status() == CourseMemberStatus.ACTIVE && member.role() == CourseMemberRole.TEACHER)
                        .isPresent(),
                course.createdAt(),
                course.updatedAt()
        );
    }

    private CoursePermissionResponse toPermission(Long courseId, Long userId, CourseMember member) {
        boolean active = member != null && member.status() == CourseMemberStatus.ACTIVE;
        boolean teacher = active && member.role() == CourseMemberRole.TEACHER;
        return new CoursePermissionResponse(courseId, userId, active, teacher,
                member == null ? null : member.role(), member == null ? null : member.status());
    }
}
