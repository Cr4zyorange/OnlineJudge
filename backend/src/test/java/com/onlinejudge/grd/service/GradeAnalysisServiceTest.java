package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshot;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshotRepository;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeRecordRepository;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeAnalysisServiceTest {
    @Test
    void teacherCalculatesCourseTotalAnalysisFromCourseRosterAndFinalScores() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                itemRepository,
                recordRepository,
                summaryRepository,
                snapshotRepository,
                permissionClient(601L, 602L, 603L, 604L)
        );
        summaryRepository.upsert(summary(101L, 601L, "84.00", FinalStatus.CALCULATED));
        summaryRepository.upsert(summary(101L, 602L, null, FinalStatus.INCOMPLETE));
        summaryRepository.upsert(summary(101L, 603L, "58.00", FinalStatus.CALCULATED));
        summaryRepository.upsert(summary(101L, 604L, "92.00", FinalStatus.ADJUSTED));

        GradeAnalysisResult result = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        assertThat(result.targetType()).isEqualTo("COURSE_TOTAL");
        assertThat(result.gradeItemId()).isNull();
        assertThat(result.totalStudentCount()).isEqualTo(4);
        assertThat(result.completedCount()).isEqualTo(3);
        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.unsubmittedCount()).isZero();
        assertThat(result.ungradedCount()).isZero();
        assertThat(result.averageScore()).isEqualByComparingTo("78.00");
        assertThat(result.maxScore()).isEqualByComparingTo("92.00");
        assertThat(result.minScore()).isEqualByComparingTo("58.00");
        assertThat(result.passRate()).isEqualByComparingTo("0.6667");
        assertThat(result.completionRate()).isEqualByComparingTo("0.7500");
        assertThat(result.distribution())
                .extracting(GradeScoreBucket::label, GradeScoreBucket::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("0-59", 1),
                        org.assertj.core.groups.Tuple.tuple("60-69", 0),
                        org.assertj.core.groups.Tuple.tuple("70-79", 0),
                        org.assertj.core.groups.Tuple.tuple("80-89", 1),
                        org.assertj.core.groups.Tuple.tuple("90-100", 1)
                );
        assertThat(result.sourceDataTime()).isNotNull();
        assertThat(result.generatedAt()).isNotNull();
        assertThat(snapshotRepository.findLatest(101L, "COURSE_TOTAL", null))
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.averageScore()).isEqualByComparingTo("78.00");
                    assertThat(snapshot.distributionJson()).contains("\"label\":\"90-100\"");
                    assertThat(snapshot.generatedBy()).isEqualTo(501L);
                });
    }

    @Test
    void teacherCalculatesGradeItemAnalysisWithMissingUngradedAndUnsubmittedCounts() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                itemRepository,
                recordRepository,
                summaryRepository,
                snapshotRepository,
                permissionClient(601L, 602L, 603L, 604L, 605L)
        );
        itemRepository.add(item(11L, 101L));
        recordRepository.upsert(record(101L, 601L, 11L, "90.00", GradeStatus.SCORED));
        recordRepository.upsert(record(101L, 602L, 11L, "70.00", GradeStatus.ADJUSTED));
        recordRepository.upsert(record(101L, 603L, 11L, null, GradeStatus.UNGRADED));
        recordRepository.upsert(record(101L, 604L, 11L, null, GradeStatus.UNSUBMITTED));
        recordRepository.upsert(record(101L, 605L, 11L, null, GradeStatus.MISSING));

        GradeAnalysisResult result = service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);

        assertThat(result.targetType()).isEqualTo("GRADE_ITEM");
        assertThat(result.gradeItemId()).isEqualTo(11L);
        assertThat(result.totalStudentCount()).isEqualTo(5);
        assertThat(result.completedCount()).isEqualTo(2);
        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.unsubmittedCount()).isEqualTo(1);
        assertThat(result.ungradedCount()).isEqualTo(1);
        assertThat(result.averageScore()).isEqualByComparingTo("80.00");
        assertThat(result.maxScore()).isEqualByComparingTo("90.00");
        assertThat(result.minScore()).isEqualByComparingTo("70.00");
        assertThat(result.passRate()).isEqualByComparingTo("1.0000");
        assertThat(result.completionRate()).isEqualByComparingTo("0.4000");
    }

    @Test
    void teacherQueriesGradeItemCompletionThroughDocumentedPublicContract() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                itemRepository,
                recordRepository,
                summaryRepository,
                snapshotRepository,
                permissionClient(601L, 602L, 603L, 604L, 605L)
        );
        itemRepository.add(item(11L, 101L));
        recordRepository.upsert(record(101L, 601L, 11L, "90.00", GradeStatus.SCORED));
        recordRepository.upsert(record(101L, 602L, 11L, "70.00", GradeStatus.ADJUSTED));
        recordRepository.upsert(record(101L, 603L, 11L, null, GradeStatus.UNGRADED));
        recordRepository.upsert(record(101L, 604L, 11L, null, GradeStatus.UNSUBMITTED));

        GradeItemCompletionResult result = service.getGradeItemCompletion(101L, 11L, 501L);

        assertThat(result.gradeItemId()).isEqualTo(11L);
        assertThat(result.totalStudentCount()).isEqualTo(5);
        assertThat(result.submittedCount()).isEqualTo(3);
        assertThat(result.completedCount()).isEqualTo(2);
        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.unsubmittedCount()).isEqualTo(1);
        assertThat(result.ungradedCount()).isEqualTo(1);
        assertThat(result.averageScore()).isEqualByComparingTo("80.00");
        assertThat(result.completionRate()).isEqualByComparingTo("0.4000");
        assertThat(result.sourceDataTime()).isNotNull();
        assertThat(result.generatedAt()).isNotNull();
        assertThat(snapshotRepository.findLatest(101L, "GRADE_ITEM", 11L)).isPresent();
    }

    @Test
    void teacherCannotQueryAnalysisWithoutCoursePermission() {
        GradeAnalysisService service = new GradeAnalysisService(
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                new InMemoryCourseGradeSummaryRepository(),
                new InMemoryGradeAnalysisSnapshotRepository(),
                (courseId, userId) -> false
        );

        assertThatThrownBy(() -> service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null))
                .isInstanceOf(GradeItemPermissionException.class)
                .hasMessageContaining("教师无课程成绩管理权限");
    }

    private CoursePermissionClient permissionClient(Long... studentIds) {
        return new CoursePermissionClient() {
            @Override
            public boolean canManageCourse(long courseId, long userId) {
                return true;
            }

            @Override
            public List<Long> listCourseStudentIds(long courseId) {
                return List.of(studentIds);
            }
        };
    }

    private GradeItem item(long id, long courseId) {
        LocalDateTime now = LocalDateTime.now();
        return new GradeItem(
                id,
                courseId,
                "实验一",
                SourceType.LAB,
                301L,
                new BigDecimal("100.00"),
                new BigDecimal("1.0000"),
                true,
                true,
                1,
                501L,
                false,
                now,
                now
        );
    }

    private GradeRecord record(long courseId, long studentId, long gradeItemId, String score, GradeStatus status) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal rawScore = score == null ? null : new BigDecimal(score);
        return new GradeRecord(
                0L,
                courseId,
                studentId,
                gradeItemId,
                SourceType.LAB,
                301L,
                rawScore,
                rawScore,
                status,
                PublishStatus.UNPUBLISHED,
                null,
                now,
                now,
                null,
                now,
                now
        );
    }

    private CourseGradeSummary summary(long courseId, long studentId, String finalScore, FinalStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new CourseGradeSummary(
                0L,
                courseId,
                studentId,
                finalScore == null ? null : new BigDecimal(finalScore),
                status,
                PublishStatus.UNPUBLISHED,
                1L,
                null,
                now,
                now
        );
    }

    private static final class InMemoryGradeItemRepository implements GradeItemRepository {
        private final List<GradeItem> items = new ArrayList<>();

        void add(GradeItem item) {
            items.add(item);
        }

        @Override
        public GradeItem save(GradeItem item) {
            items.add(item);
            return item;
        }

        @Override
        public GradeItem update(GradeItem item) {
            return item;
        }

        @Override
        public Optional<GradeItem> findById(long id) {
            return items.stream().filter(item -> item.id() == id).findFirst();
        }

        @Override
        public List<GradeItem> findByCourseId(long courseId) {
            return items.stream().filter(item -> item.courseId() == courseId).toList();
        }
    }

    private static final class InMemoryGradeRecordRepository implements GradeRecordRepository {
        private long nextId = 1L;
        private final List<GradeRecord> records = new ArrayList<>();

        @Override
        public GradeRecord upsert(GradeRecord record) {
            GradeRecord saved = record.withId(nextId++);
            records.add(saved);
            return saved;
        }

        @Override
        public GradeRecord update(GradeRecord record) {
            return record;
        }

        @Override
        public Optional<GradeRecord> findById(long id) {
            return records.stream().filter(record -> record.id() == id).findFirst();
        }

        @Override
        public List<GradeRecord> findByCourseId(long courseId) {
            return records.stream().filter(record -> record.courseId() == courseId).toList();
        }
    }

    private static final class InMemoryCourseGradeSummaryRepository implements CourseGradeSummaryRepository {
        private final List<CourseGradeSummary> summaries = new ArrayList<>();

        @Override
        public CourseGradeSummary upsert(CourseGradeSummary summary) {
            summaries.add(summary);
            return summary;
        }

        @Override
        public CourseGradeSummary update(CourseGradeSummary summary) {
            return summary;
        }

        @Override
        public Optional<CourseGradeSummary> findById(long id) {
            return summaries.stream().filter(summary -> summary.id() == id).findFirst();
        }

        @Override
        public List<CourseGradeSummary> findByCourseId(long courseId) {
            return summaries.stream().filter(summary -> summary.courseId() == courseId).toList();
        }
    }

    private static final class InMemoryGradeAnalysisSnapshotRepository implements GradeAnalysisSnapshotRepository {
        private long nextId = 1L;
        private final List<GradeAnalysisSnapshot> snapshots = new ArrayList<>();

        @Override
        public GradeAnalysisSnapshot save(GradeAnalysisSnapshot snapshot) {
            GradeAnalysisSnapshot saved = snapshot.withId(nextId++);
            snapshots.add(saved);
            return saved;
        }

        @Override
        public Optional<GradeAnalysisSnapshot> findLatest(long courseId, String targetType, Long gradeItemId) {
            return snapshots.stream()
                    .filter(snapshot -> snapshot.courseId() == courseId)
                    .filter(snapshot -> snapshot.targetType().equals(targetType))
                    .filter(snapshot -> gradeItemId == null || gradeItemId.equals(snapshot.gradeItemId()))
                    .reduce((first, second) -> second);
        }
    }
}
