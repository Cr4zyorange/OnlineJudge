package com.onlinejudge.contracts;

import com.onlinejudge.crs.domain.Course;
import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.CourseStatus;
import com.onlinejudge.crs.domain.EnrollmentMode;
import com.onlinejudge.crs.mapper.CourseRepository;
import com.onlinejudge.crs.service.CrsCoursePermissionProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #310 C-03 生产者契约：CRS 通过 CoursePermissionProvider 提供课程存在性、
 * 成员关系、管理权限和名单，不把 CRS 内部表暴露给消费方。
 */
class CoursePermissionProducerContractTest {

    @Test
    void exposesCourseExistenceMembershipAndManagePermissionFromCrsOwnedData() {
        CourseRepository repository = new StubCourseRepository(
                List.of(
                        member(101L, 501L, CourseMemberRole.TEACHER, CourseMemberStatus.ACTIVE),
                        member(101L, 601L, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE),
                        member(101L, 602L, CourseMemberRole.STUDENT, CourseMemberStatus.REMOVED),
                        member(101L, 603L, CourseMemberRole.ASSISTANT, CourseMemberStatus.ACTIVE),
                        member(202L, 501L, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE)
                )
        );
        CrsCoursePermissionProvider provider = new CrsCoursePermissionProvider(repository);

        assertThat(provider.courseExists(101L)).isTrue();
        assertThat(provider.courseExists(303L)).isFalse();
        assertThat(provider.canViewCourse(101L, 601L)).isTrue();
        assertThat(provider.canViewCourse(101L, 602L)).isFalse();
        assertThat(provider.canViewCourse(303L, 601L)).isFalse();
        assertThat(provider.canManageCourse(101L, 501L)).isTrue();
        assertThat(provider.canManageCourse(101L, 603L)).isTrue();
        assertThat(provider.canManageCourse(101L, 601L)).isFalse();
        assertThat(provider.canManageCourse(202L, 501L)).isFalse();
    }

    @Test
    void exposesActiveStudentAndTeacherRostersWithoutRemovedMembers() {
        CourseRepository repository = new StubCourseRepository(
                List.of(
                        member(101L, 501L, CourseMemberRole.TEACHER, CourseMemberStatus.ACTIVE),
                        member(101L, 503L, CourseMemberRole.TEACHER, CourseMemberStatus.REMOVED),
                        member(101L, 601L, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE),
                        member(101L, 602L, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE)
                )
        );
        CrsCoursePermissionProvider provider = new CrsCoursePermissionProvider(repository);

        assertThat(provider.listActiveStudentIds(101L)).containsExactly(601L, 602L);
        assertThat(provider.listActiveTeacherIds(101L)).containsExactly(501L);
        assertThat(provider.listActiveStudentIds(303L)).isEmpty();
    }

    @Test
    void invalidIdentifiersAreRejectedWithoutQuerying() {
        CourseRepository repository = new StubCourseRepository(List.of());
        CrsCoursePermissionProvider provider = new CrsCoursePermissionProvider(repository);

        assertThat(provider.courseExists(0L)).isFalse();
        assertThat(provider.canViewCourse(101L, 0L)).isFalse();
        assertThat(provider.canManageCourse(0L, 501L)).isFalse();
    }

    private CourseMember member(long courseId, long userId, CourseMemberRole role, CourseMemberStatus status) {
        return new CourseMember(1L, courseId, userId, role, "TEST", status, 501L, LocalDateTime.now());
    }

    private static final class StubCourseRepository extends CourseRepository {
        private final List<CourseMember> members;
        private final Course course = new Course(
                101L, "Software Engineering", "course", 501L, "2026-S",
                "SE", null, EnrollmentMode.PUBLIC, "INVITE", 100,
                LocalDate.of(2026, 2, 20), LocalDate.of(2026, 7, 10),
                CourseStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        );

        private StubCourseRepository(List<CourseMember> members) {
            super(null);
            this.members = members;
        }

        @Override
        public Optional<Course> findById(Long courseId) {
            return courseId != null && courseId == 101L ? Optional.of(course) : Optional.empty();
        }

        @Override
        public Optional<CourseMember> findMember(Long courseId, Long userId) {
            return members.stream()
                    .filter(member -> member.courseId().equals(courseId) && member.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<Long> listActiveStudentIds(Long courseId) {
            return members.stream()
                    .filter(member -> member.courseId().equals(courseId))
                    .filter(member -> member.role() == CourseMemberRole.STUDENT)
                    .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                    .map(CourseMember::userId)
                    .toList();
        }

        @Override
        public List<Long> listActiveTeacherIds(long courseId) {
            return members.stream()
                    .filter(member -> member.courseId() == courseId)
                    .filter(member -> member.role() == CourseMemberRole.TEACHER
                            || member.role() == CourseMemberRole.ASSISTANT)
                    .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                    .map(CourseMember::userId)
                    .toList();
        }
    }
}
