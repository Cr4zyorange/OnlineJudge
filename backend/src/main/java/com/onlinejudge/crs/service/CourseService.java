package com.onlinejudge.crs.service;

import com.onlinejudge.common.exception.BusinessException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.crs.domain.Course;
import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.CourseStatus;
import com.onlinejudge.crs.domain.EnrollmentMode;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.domain.dto.CourseJoinRequest;
import com.onlinejudge.crs.domain.dto.CourseMemberResponse;
import com.onlinejudge.crs.domain.dto.CourseMemberUpdateRequest;
import com.onlinejudge.crs.domain.dto.CoursePermissionResponse;
import com.onlinejudge.crs.domain.dto.CourseResponse;
import com.onlinejudge.crs.domain.dto.CourseUpdateRequest;
import com.onlinejudge.crs.mapper.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        boolean admin = isAdmin(user);
        return new PageResponse<>(
                courseRepository.list(keyword, normalizedPage, normalizedSize, normalizedScope, user.id(), admin, user.hasRole("TEACHER")).stream()
                        .map(course -> toResponse(course, user))
                        .toList(),
                courseRepository.count(keyword, normalizedScope, user.id(), admin, user.hasRole("TEACHER")),
                normalizedPage,
                normalizedSize
        );
    }

    public CourseResponse detail(Long courseId, CurrentUser user) {
        Course course = getCourse(courseId);
        if (!isAdmin(user) && !isActiveMember(courseId, user.id())) {
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
    public CoursePermissionResponse join(Long courseId, CourseJoinRequest request, CurrentUser user) {
        Course course = getCourse(courseId);
        if (course.status() == CourseStatus.ARCHIVED || course.status() == CourseStatus.CLOSED) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURSE_CLOSED");
        }
        validateInviteCode(course, request);
        CourseMemberStatus targetStatus = course.enrollmentMode() == EnrollmentMode.REVIEW
                ? CourseMemberStatus.PENDING
                : CourseMemberStatus.ACTIVE;
        String method = course.enrollmentMode().name();
        var existingMember = courseRepository.findMember(courseId, user.id());
        if (existingMember.isPresent()) {
            CourseMember member = existingMember.get();
            if (member.status() == CourseMemberStatus.ACTIVE) {
                throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_JOINED");
            }
            if (member.status() == CourseMemberStatus.PENDING) {
                throw new BusinessException(HttpStatus.CONFLICT, "JOIN_PENDING");
            }
            requireCapacity(course, targetStatus);
            return toPermission(courseId, user.id(),
                    courseRepository.updateMemberForJoin(courseId, user.id(), targetStatus, method, null));
        }
        requireCapacity(course, targetStatus);
        CourseMember member = courseRepository.insertMember(courseId, user.id(), CourseMemberRole.STUDENT, targetStatus, method, null);
        return toPermission(courseId, user.id(), member);
    }

    public CoursePermissionResponse permission(Long courseId, Long userId) {
        getCourse(courseId);
        return toPermission(courseId, userId, courseRepository.findMember(courseId, userId).orElse(null));
    }

    public List<CourseMemberResponse> members(Long courseId, CourseMemberStatus status, CurrentUser user) {
        requireActiveMembership(courseId, user);
        return courseRepository.listMembers(courseId, status).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    public List<Long> students(Long courseId, CurrentUser user) {
        requireStudentRosterPermission(courseId, user);
        return courseRepository.listActiveStudentIds(courseId);
    }

    @Transactional
    public CourseMemberResponse updateMember(Long courseId, Long userId, CourseMemberUpdateRequest request, CurrentUser user) {
        requireManagePermission(courseId, user);
        CourseMember member = courseRepository.findMember(courseId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND"));
        CourseMemberRole role = request.role() == null ? member.role() : request.role();
        CourseMemberStatus status = request.status();
        validateMemberTransition(member.status(), status);
        if (user.id() == userId && (role != CourseMemberRole.TEACHER || status == CourseMemberStatus.REMOVED)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_CHANGE_SELF_TEACHER");
        }
        if (member.role() == CourseMemberRole.TEACHER
                && (role != CourseMemberRole.TEACHER || status == CourseMemberStatus.REMOVED)
                && courseRepository.activeTeacherCount(courseId) <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "LAST_TEACHER_REQUIRED");
        }
        if (willAddActiveStudent(member, role, status)) {
            requireCapacity(getCourse(courseId), CourseMemberStatus.ACTIVE);
        }
        if (status == CourseMemberStatus.REJECTED && member.status() != CourseMemberStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "ONLY_PENDING_CAN_BE_REJECTED");
        }
        return toMemberResponse(courseRepository.updateMember(courseId, userId, role, status, user.id()));
    }

    @Transactional
    public void removeMember(Long courseId, Long userId, CurrentUser user) {
        requireManagePermission(courseId, user);
        CourseMember member = courseRepository.findMember(courseId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND"));
        if (user.id() == userId) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_REMOVE_SELF");
        }
        if (member.role() == CourseMemberRole.TEACHER && courseRepository.activeTeacherCount(courseId) <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "LAST_TEACHER_REQUIRED");
        }
        validateMemberTransition(member.status(), CourseMemberStatus.REMOVED);
        courseRepository.updateMember(courseId, userId, member.role(), CourseMemberStatus.REMOVED, user.id());
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "课程不存在"));
    }

    private void requireTeacher(CurrentUser user) {
        if (!isTeacher(user)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
        }
    }

    private void requireManagePermission(Long courseId, CurrentUser user) {
        getCourse(courseId);
        if (isAdmin(user)) {
            return;
        }
        CourseMember member = courseRepository.findMember(courseId, user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "无权限访问"));
        if (member.status() != CourseMemberStatus.ACTIVE || member.role() != CourseMemberRole.TEACHER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
        }
    }

    private void requireStudentRosterPermission(Long courseId, CurrentUser user) {
        getCourse(courseId);
        if (isAdmin(user) || user.hasPermission("course:students:read") || user.hasPermission("course:manage")) {
            return;
        }
        requireManagePermission(courseId, user);
    }

    private void requireActiveMembership(Long courseId, CurrentUser user) {
        getCourse(courseId);
        if (isAdmin(user)) {
            return;
        }
        if (!isActiveMember(courseId, user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "NO_COURSE_MEMBERSHIP");
        }
    }

    private void validateMemberTransition(CourseMemberStatus current, CourseMemberStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == CourseMemberStatus.ACTIVE || target == CourseMemberStatus.REJECTED;
            case ACTIVE -> target == CourseMemberStatus.ACTIVE || target == CourseMemberStatus.REMOVED;
            case REJECTED, REMOVED -> false;
        };
        if (!valid) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_MEMBER_STATUS_TRANSITION");
        }
    }

    private boolean willAddActiveStudent(CourseMember current, CourseMemberRole targetRole, CourseMemberStatus targetStatus) {
        if (targetStatus != CourseMemberStatus.ACTIVE || targetRole != CourseMemberRole.STUDENT) {
            return false;
        }
        return current.status() != CourseMemberStatus.ACTIVE || current.role() != CourseMemberRole.STUDENT;
    }

    private boolean isActiveMember(Long courseId, Long userId) {
        return courseRepository.findMember(courseId, userId)
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                .isPresent();
    }

    private void validateInviteCode(Course course, CourseJoinRequest request) {
        if (course.enrollmentMode() != EnrollmentMode.INVITE) {
            return;
        }
        String expected = normalize(course.inviteCode());
        String actual = normalize(request == null ? null : request.inviteCode());
        if (expected == null || actual == null || !expected.equals(actual)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INVITE_CODE");
        }
    }

    private void requireCapacity(Course course, CourseMemberStatus targetStatus) {
        if (targetStatus != CourseMemberStatus.ACTIVE || course.maxStudents() == null) {
            return;
        }
        if (courseRepository.activeStudentCount(course.id()) >= course.maxStudents()) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURSE_FULL");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private CourseResponse toResponse(Course course, CurrentUser viewer) {
        boolean manageable = isAdmin(viewer) || courseRepository.findMember(course.id(), viewer.id())
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE && member.role() == CourseMemberRole.TEACHER)
                .isPresent();
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
                manageable ? course.inviteCode() : null,
                course.maxStudents(),
                course.startDate(),
                course.endDate(),
                course.status(),
                courseRepository.memberCount(course.id()),
                isAdmin(viewer) || isActiveMember(course.id(), viewer.id()),
                manageable,
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

    private CourseMemberResponse toMemberResponse(CourseMember member) {
        return new CourseMemberResponse(
                member.courseId(),
                member.userId(),
                member.role(),
                member.status(),
                member.joinMethod(),
                member.approvedBy(),
                member.joinedAt()
        );
    }

    private boolean isAdmin(CurrentUser user) {
        return user.hasRole("ADMIN");
    }

    private boolean isTeacher(CurrentUser user) {
        return user.hasRole("TEACHER") || isAdmin(user);
    }
}
