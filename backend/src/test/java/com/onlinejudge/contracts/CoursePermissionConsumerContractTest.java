package com.onlinejudge.contracts;

import com.onlinejudge.integration.course.CoursePermissionProvider;
import com.onlinejudge.integration.course.DefaultCoursePermissionClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #310 C-03 CoursePermissionClient 消费端契约：
 * 成功、拒绝、重复、超时、下游不可用五类行为必须可独立验证。
 */
class CoursePermissionConsumerContractTest {

    @Test
    void successPathResolvesMemberAndManagePermissionsFromProducer() {
        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(false, provider(
                true, true, List.of(601L), List.of(501L)
        ));

        assertThat(client.courseExists(101L)).isTrue();
        assertThat(client.isCourseMember(101L, 601L)).isTrue();
        assertThat(client.canViewCourse(101L, 601L)).isTrue();
        assertThat(client.canManageCourse(101L, 501L)).isTrue();
        assertThat(client.canManageCourseGrade(101L, 501L)).isTrue();
        assertThat(client.listCourseStudentIds(101L)).containsExactly(601L);
        assertThat(client.listCourseTeacherIds(101L)).containsExactly(501L);
    }

    @Test
    void rejectionIsFailClosedWhenProducerDenies() {
        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(false, provider(
                false, false, List.of(), List.of()
        ));

        assertThat(client.courseExists(101L)).isFalse();
        assertThat(client.isCourseMember(101L, 601L)).isFalse();
        assertThat(client.canViewCourse(101L, 601L)).isFalse();
        assertThat(client.canManageCourse(101L, 501L)).isFalse();
    }

    @Test
    void timeoutIsFailClosedAndNeverLeaksPartialAuthority() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CoursePermissionProvider blockingProvider = new CoursePermissionProvider() {
            @Override
            public boolean courseExists(long courseId) {
                await(interrupted);
                return true;
            }

            @Override
            public boolean canManageCourse(long courseId, long userId) {
                await(interrupted);
                return true;
            }

            @Override
            public boolean canViewCourse(long courseId, long userId) {
                await(interrupted);
                return true;
            }

            @Override
            public List<Long> listActiveStudentIds(long courseId) {
                await(interrupted);
                return List.of(601L);
            }

            @Override
            public List<Long> listActiveTeacherIds(long courseId) {
                await(interrupted);
                return List.of(501L);
            }

            private void await(AtomicBoolean interruptedFlag) {
                try {
                    blockLatch.await();
                } catch (InterruptedException exception) {
                    interruptedFlag.set(true);
                    Thread.currentThread().interrupt();
                }
            }
        };
        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(
                false, blockingProvider, Duration.ofMillis(50)
        );

        try {
            long startedAt = System.nanoTime();
            assertThat(client.courseExists(101L)).isFalse();
            assertThat(client.canViewCourse(101L, 601L)).isFalse();
            assertThat(client.canManageCourse(101L, 501L)).isFalse();
            assertThat(client.listCourseStudentIds(101L)).isEmpty();
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            assertThat(elapsedMs).isLessThan(5000L);

            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (!interrupted.get() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(interrupted)
                    .as("超时后后台 CRS Provider 任务必须被取消，不能遗留永不结束的调用")
                    .isTrue();
        } finally {
            client.shutdown();
        }
    }

    @Test
    void downstreamUnavailableIsFailClosedInsteadOfReachingIntoCrsTables() {
        CoursePermissionProvider brokenProvider = new CoursePermissionProvider() {
            @Override
            public boolean courseExists(long courseId) {
                throw new IllegalStateException("CRS service unavailable");
            }

            @Override
            public boolean canManageCourse(long courseId, long userId) {
                throw new IllegalStateException("CRS service unavailable");
            }

            @Override
            public boolean canViewCourse(long courseId, long userId) {
                throw new IllegalStateException("CRS service unavailable");
            }

            @Override
            public List<Long> listActiveStudentIds(long courseId) {
                throw new IllegalStateException("CRS service unavailable");
            }

            @Override
            public List<Long> listActiveTeacherIds(long courseId) {
                throw new IllegalStateException("CRS service unavailable");
            }
        };
        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(false, brokenProvider);

        assertThat(client.courseExists(101L)).isFalse();
        assertThat(client.canViewCourse(101L, 601L)).isFalse();
        assertThat(client.canManageCourse(101L, 501L)).isFalse();
        assertThat(client.listCourseStudentIds(101L)).isEmpty();
    }

    @Test
    void duplicateCallsAreIdempotentAndStable() {
        DefaultCoursePermissionClient client = new DefaultCoursePermissionClient(false, provider(
                true, true, List.of(601L, 602L), List.of(501L)
        ));

        assertThat(client.canViewCourse(101L, 601L)).isTrue();
        assertThat(client.canViewCourse(101L, 601L)).isTrue();
        assertThat(client.canManageCourse(101L, 501L)).isTrue();
        assertThat(client.canManageCourse(101L, 501L)).isTrue();
        assertThat(client.listCourseStudentIds(101L)).containsExactly(601L, 602L);
        assertThat(client.listCourseStudentIds(101L)).containsExactly(601L, 602L);
    }

    private static CoursePermissionProvider provider(
            boolean exists,
            boolean manage,
            List<Long> students,
            List<Long> teachers
    ) {
        return new CoursePermissionProvider() {
            @Override
            public boolean courseExists(long courseId) {
                return exists;
            }

            @Override
            public boolean canManageCourse(long courseId, long userId) {
                return manage;
            }

            @Override
            public boolean canViewCourse(long courseId, long userId) {
                return exists;
            }

            @Override
            public List<Long> listActiveStudentIds(long courseId) {
                return students;
            }

            @Override
            public List<Long> listActiveTeacherIds(long courseId) {
                return teachers;
            }
        };
    }
}
