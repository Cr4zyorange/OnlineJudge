package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshot;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshotRepository;
import com.onlinejudge.grd.domain.GradeAnalysisSourceVersion;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeAnalysisServiceTest {
    @Test
    void unchangedCourseTotalAnalysisReusesTheLatestSnapshot() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                summaryRepository,
                snapshotRepository,
                permissionClient(601L)
        );
        summaryRepository.upsert(summary(101L, 601L, "84.00", FinalStatus.CALCULATED));

        GradeAnalysisResult first = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);
        GradeAnalysisResult second = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(second.sourceDataTime()).isEqualTo(first.sourceDataTime());
        assertThat(snapshotRepository.size()).isEqualTo(1);
        assertThat(summaryRepository.findByCourseIdCallCount()).isEqualTo(1);
    }

    @Test
    void unchangedGradeItemAnalysisReusesTheLatestSnapshot() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                itemRepository,
                recordRepository,
                new InMemoryCourseGradeSummaryRepository(),
                snapshotRepository,
                permissionClient(601L)
        );
        itemRepository.add(item(11L, 101L));
        recordRepository.upsert(record(101L, 601L, 11L, "90.00", GradeStatus.SCORED));

        GradeAnalysisResult first = service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);
        GradeAnalysisResult second = service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);

        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(second.sourceDataTime()).isEqualTo(first.sourceDataTime());
        assertThat(snapshotRepository.size()).isEqualTo(1);
        assertThat(recordRepository.findByCourseIdCallCount()).isEqualTo(1);
    }

    @Test
    void sourceFingerprintCarriesAnExplicitAnalysisContractVersion() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                summaryRepository,
                snapshotRepository,
                permissionClient(601L)
        );
        summaryRepository.upsert(summary(101L, 601L, "84.00", FinalStatus.CALCULATED));

        service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        assertThat(snapshotRepository.findLatest(101L, "COURSE_TOTAL", null).orElseThrow().sourceFingerprint())
                .startsWith("GRD_ANALYSIS_V2:");
    }

    @Test
    void changedAnalysisContractVersionInvalidatesAnOtherwiseMatchingSnapshot() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                summaryRepository,
                snapshotRepository,
                permissionClient(601L)
        );
        summaryRepository.upsert(summary(101L, 601L, "84.00", FinalStatus.CALCULATED));
        service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);
        snapshotRepository.replaceLatestFingerprintContract("GRD_ANALYSIS_V1");

        service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        assertThat(snapshotRepository.size()).isEqualTo(2);
        assertThat(summaryRepository.findByCourseIdCallCount()).isEqualTo(2);
        assertThat(snapshotRepository.findLatest(101L, "COURSE_TOTAL", null).orElseThrow().sourceFingerprint())
                .startsWith("GRD_ANALYSIS_V2:");
    }

    @Test
    void changedCourseTotalCreatesANewSnapshotEvenWhenUpdateTimestampIsUnchanged() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                summaryRepository,
                snapshotRepository,
                permissionClient(601L)
        );
        LocalDateTime firstSourceTime = LocalDateTime.of(2026, 8, 25, 9, 0);
        summaryRepository.upsert(summaryAt(101L, 601L, "84.00", FinalStatus.CALCULATED, firstSourceTime));
        GradeAnalysisResult first = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        summaryRepository.upsert(summaryAt(101L, 601L, "92.00", FinalStatus.ADJUSTED, firstSourceTime));
        GradeAnalysisResult changed = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        assertThat(changed.averageScore()).isEqualByComparingTo("92.00");
        assertThat(changed.sourceDataTime()).isAfter(first.sourceDataTime());
        assertThat(changed.generatedAt()).isNotEqualTo(first.generatedAt());
        assertThat(snapshotRepository.size()).isEqualTo(2);
        assertThat(summaryRepository.findByCourseIdCallCount()).isEqualTo(2);
    }

    @Test
    void changedGradeItemStatusCreatesANewSnapshotEvenWhenScoreAndUpdateTimestampAreUnchanged() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                itemRepository,
                recordRepository,
                new InMemoryCourseGradeSummaryRepository(),
                snapshotRepository,
                permissionClient(601L)
        );
        itemRepository.add(item(11L, 101L));
        LocalDateTime firstSourceTime = LocalDateTime.of(2026, 8, 25, 9, 0);
        recordRepository.upsert(recordAt(101L, 601L, 11L, null, GradeStatus.UNSUBMITTED, firstSourceTime));
        GradeAnalysisResult first = service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);

        recordRepository.upsert(recordAt(101L, 601L, 11L, null, GradeStatus.UNGRADED, firstSourceTime));
        GradeAnalysisResult changed = service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);

        assertThat(first.unsubmittedCount()).isEqualTo(1);
        assertThat(changed.unsubmittedCount()).isZero();
        assertThat(changed.ungradedCount()).isEqualTo(1);
        assertThat(changed.sourceDataTime()).isAfter(first.sourceDataTime());
        assertThat(snapshotRepository.size()).isEqualTo(2);
        assertThat(recordRepository.findByCourseIdCallCount()).isEqualTo(2);
    }

    @Test
    void changedActiveStudentRosterInvalidatesAnOtherwiseEmptyCourseSnapshot() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        MutableCoursePermissionClient permissionClient = new MutableCoursePermissionClient();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        permissionClient.setStudentIds(101L, 601L);
        GradeAnalysisService service = new GradeAnalysisService(
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                summaryRepository,
                snapshotRepository,
                permissionClient
        );
        GradeAnalysisResult first = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);
        GradeAnalysisResult reused = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        permissionClient.setStudentIds(101L, 601L, 602L);
        GradeAnalysisResult addedStudent = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);
        permissionClient.setStudentIds(101L, 602L);
        GradeAnalysisResult removedStudent = service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);

        assertThat(reused.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(addedStudent.totalStudentCount()).isEqualTo(2);
        assertThat(addedStudent.missingCount()).isEqualTo(2);
        assertThat(addedStudent.sourceDataTime()).isAfter(first.sourceDataTime());
        assertThat(removedStudent.totalStudentCount()).isEqualTo(1);
        assertThat(removedStudent.sourceDataTime()).isAfter(addedStudent.sourceDataTime());
        assertThat(snapshotRepository.size()).isEqualTo(3);
        assertThat(summaryRepository.findByCourseIdCallCount()).isEqualTo(3);
    }

    @Test
    void missingUnsubmittedAndUngradedRowsHaveStableSnapshotReuse() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        GradeAnalysisService service = new GradeAnalysisService(
                itemRepository,
                recordRepository,
                new InMemoryCourseGradeSummaryRepository(),
                snapshotRepository,
                permissionClient(601L, 602L, 603L, 604L)
        );
        itemRepository.add(item(11L, 101L));
        LocalDateTime sourceTime = LocalDateTime.of(2026, 8, 25, 9, 0);
        recordRepository.upsert(recordAt(101L, 602L, 11L, null, GradeStatus.UNSUBMITTED, sourceTime));
        recordRepository.upsert(recordAt(101L, 603L, 11L, null, GradeStatus.UNGRADED, sourceTime));
        recordRepository.upsert(recordAt(101L, 604L, 11L, null, GradeStatus.MISSING, sourceTime));

        GradeAnalysisResult first = service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);
        GradeAnalysisResult second = service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);

        assertThat(second.missingCount()).isEqualTo(2);
        assertThat(second.unsubmittedCount()).isEqualTo(1);
        assertThat(second.ungradedCount()).isEqualTo(1);
        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(second.sourceDataTime()).isEqualTo(sourceTime);
        assertThat(snapshotRepository.size()).isEqualTo(1);
    }

    @Test
    void snapshotsRemainIsolatedByCourseTargetAndGradeItem() {
        InMemoryGradeAnalysisSnapshotRepository snapshotRepository = new InMemoryGradeAnalysisSnapshotRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        MutableCoursePermissionClient permissionClient = new MutableCoursePermissionClient();
        permissionClient.setStudentIds(101L, 601L);
        permissionClient.setStudentIds(202L, 701L);
        GradeAnalysisService service = new GradeAnalysisService(
                itemRepository,
                recordRepository,
                summaryRepository,
                snapshotRepository,
                permissionClient
        );
        itemRepository.add(item(11L, 101L));
        itemRepository.add(item(12L, 101L));
        itemRepository.add(item(21L, 202L));
        summaryRepository.upsert(summary(101L, 601L, "80.00", FinalStatus.CALCULATED));
        recordRepository.upsert(record(101L, 601L, 11L, "81.00", GradeStatus.SCORED));
        recordRepository.upsert(record(101L, 601L, 12L, "82.00", GradeStatus.SCORED));
        recordRepository.upsert(record(202L, 701L, 21L, "91.00", GradeStatus.SCORED));

        service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);
        service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);
        service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 12L);
        service.analyzeCourseGrades(202L, 501L, "GRADE_ITEM", 21L);
        service.analyzeCourseGrades(101L, 501L, "COURSE_TOTAL", null);
        service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 11L);
        service.analyzeCourseGrades(101L, 501L, "GRADE_ITEM", 12L);
        service.analyzeCourseGrades(202L, 501L, "GRADE_ITEM", 21L);

        assertThat(snapshotRepository.size()).isEqualTo(4);
        assertThat(snapshotRepository.findLatest(101L, "COURSE_TOTAL", null)).isPresent();
        assertThat(snapshotRepository.findLatest(101L, "GRADE_ITEM", 11L)).isPresent();
        assertThat(snapshotRepository.findLatest(101L, "GRADE_ITEM", 12L)).isPresent();
        assertThat(snapshotRepository.findLatest(202L, "GRADE_ITEM", 21L)).isPresent();
    }

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
        GradeItemCompletionResult reused = service.getGradeItemCompletion(101L, 11L, 501L);

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
        assertThat(reused.generatedAt()).isEqualTo(result.generatedAt());
        assertThat(recordRepository.findByCourseIdCallCount()).isEqualTo(1);
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
        return recordAt(courseId, studentId, gradeItemId, score, status, LocalDateTime.now());
    }

    private GradeRecord recordAt(
            long courseId,
            long studentId,
            long gradeItemId,
            String score,
            GradeStatus status,
            LocalDateTime updatedAt
    ) {
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
                updatedAt,
                updatedAt,
                null,
                updatedAt,
                updatedAt
        );
    }

    private CourseGradeSummary summary(long courseId, long studentId, String finalScore, FinalStatus status) {
        return summaryAt(courseId, studentId, finalScore, status, LocalDateTime.now());
    }

    private CourseGradeSummary summaryAt(
            long courseId,
            long studentId,
            String finalScore,
            FinalStatus status,
            LocalDateTime updatedAt
    ) {
        return new CourseGradeSummary(
                0L,
                courseId,
                studentId,
                finalScore == null ? null : new BigDecimal(finalScore),
                status,
                PublishStatus.UNPUBLISHED,
                1L,
                null,
                updatedAt,
                updatedAt
        );
    }

    private static final class MutableCoursePermissionClient implements CoursePermissionClient {
        private final Map<Long, List<Long>> studentIdsByCourse = new LinkedHashMap<>();

        void setStudentIds(long courseId, Long... studentIds) {
            studentIdsByCourse.put(courseId, List.of(studentIds));
        }

        @Override
        public boolean canManageCourse(long courseId, long userId) {
            return true;
        }

        @Override
        public List<Long> listCourseStudentIds(long courseId) {
            return studentIdsByCourse.getOrDefault(courseId, List.of());
        }
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
        private int findByCourseIdCallCount;
        private final List<GradeRecord> records = new ArrayList<>();
        private final Map<String, GradeAnalysisSourceVersion> sourceVersions = new LinkedHashMap<>();

        @Override
        public GradeRecord upsert(GradeRecord record) {
            GradeRecord saved = record.withId(nextId++);
            records.add(saved);
            bumpSourceVersion(saved.courseId(), saved.gradeItemId(), saved.updatedAt());
            return saved;
        }

        @Override
        public GradeRecord update(GradeRecord record) {
            bumpSourceVersion(record.courseId(), record.gradeItemId(), record.updatedAt());
            return record;
        }

        @Override
        public Optional<GradeRecord> findById(long id) {
            return records.stream().filter(record -> record.id() == id).findFirst();
        }

        @Override
        public List<GradeRecord> findByCourseId(long courseId) {
            findByCourseIdCallCount++;
            return records.stream().filter(record -> record.courseId() == courseId).toList();
        }

        int findByCourseIdCallCount() {
            return findByCourseIdCallCount;
        }

        @Override
        public GradeAnalysisSourceVersion findAnalysisSourceVersion(long courseId, long gradeItemId) {
            return sourceVersions.getOrDefault(sourceKey(courseId, gradeItemId), GradeAnalysisSourceVersion.initial());
        }

        private void bumpSourceVersion(long courseId, long gradeItemId, LocalDateTime sourceDataTime) {
            String key = sourceKey(courseId, gradeItemId);
            GradeAnalysisSourceVersion previous = sourceVersions.getOrDefault(key, GradeAnalysisSourceVersion.initial());
            sourceVersions.put(key, new GradeAnalysisSourceVersion(previous.version() + 1, sourceDataTime));
        }

        private String sourceKey(long courseId, long gradeItemId) {
            return courseId + ":" + gradeItemId;
        }
    }

    private static final class InMemoryCourseGradeSummaryRepository implements CourseGradeSummaryRepository {
        private int findByCourseIdCallCount;
        private final List<CourseGradeSummary> summaries = new ArrayList<>();
        private final Map<Long, GradeAnalysisSourceVersion> sourceVersions = new LinkedHashMap<>();

        @Override
        public CourseGradeSummary upsert(CourseGradeSummary summary) {
            summaries.add(summary);
            bumpSourceVersion(summary.courseId(), summary.updatedAt());
            return summary;
        }

        @Override
        public CourseGradeSummary update(CourseGradeSummary summary) {
            bumpSourceVersion(summary.courseId(), summary.updatedAt());
            return summary;
        }

        @Override
        public Optional<CourseGradeSummary> findById(long id) {
            return summaries.stream().filter(summary -> summary.id() == id).findFirst();
        }

        @Override
        public List<CourseGradeSummary> findByCourseId(long courseId) {
            findByCourseIdCallCount++;
            return summaries.stream().filter(summary -> summary.courseId() == courseId).toList();
        }

        int findByCourseIdCallCount() {
            return findByCourseIdCallCount;
        }

        @Override
        public GradeAnalysisSourceVersion findAnalysisSourceVersion(long courseId) {
            return sourceVersions.getOrDefault(courseId, GradeAnalysisSourceVersion.initial());
        }

        private void bumpSourceVersion(long courseId, LocalDateTime sourceDataTime) {
            GradeAnalysisSourceVersion previous = sourceVersions.getOrDefault(
                    courseId,
                    GradeAnalysisSourceVersion.initial()
            );
            sourceVersions.put(courseId, new GradeAnalysisSourceVersion(previous.version() + 1, sourceDataTime));
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

        int size() {
            return snapshots.size();
        }

        void replaceLatestFingerprintContract(String contractVersion) {
            GradeAnalysisSnapshot latest = snapshots.remove(snapshots.size() - 1);
            String fingerprint = latest.sourceFingerprint();
            String digest = fingerprint.substring(fingerprint.indexOf(':') + 1);
            snapshots.add(new GradeAnalysisSnapshot(
                    latest.id(),
                    latest.courseId(),
                    latest.targetType(),
                    latest.gradeItemId(),
                    latest.sourceDataTime(),
                    contractVersion + ":" + digest,
                    latest.averageScore(),
                    latest.maxScore(),
                    latest.minScore(),
                    latest.passRate(),
                    latest.completionRate(),
                    latest.totalStudentCount(),
                    latest.completedCount(),
                    latest.missingCount(),
                    latest.unsubmittedCount(),
                    latest.ungradedCount(),
                    latest.distributionJson(),
                    latest.generatedBy(),
                    latest.generatedAt()
            ));
        }
    }
}
