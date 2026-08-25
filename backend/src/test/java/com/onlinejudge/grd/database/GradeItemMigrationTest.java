package com.onlinejudge.grd.database;

import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshot;
import com.onlinejudge.grd.domain.GradeChangeLog;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.grd.repository.JdbcCourseGradeSummaryRepository;
import com.onlinejudge.grd.repository.JdbcGradeAnalysisSnapshotRepository;
import com.onlinejudge.grd.repository.JdbcGradeChangeLogRepository;
import com.onlinejudge.grd.repository.JdbcGradeItemRepository;
import com.onlinejudge.grd.repository.JdbcGradeRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grade_item_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JdbcGradeItemRepository.class,
        JdbcGradeRecordRepository.class,
        JdbcCourseGradeSummaryRepository.class,
        JdbcGradeAnalysisSnapshotRepository.class,
        JdbcGradeChangeLogRepository.class
})
@Sql(scripts = "file:../database/migrations/20260525_01_create_grd_grade_item.sql")
class GradeItemMigrationTest {
    private final JdbcGradeItemRepository repository;
    private final JdbcGradeRecordRepository gradeRecordRepository;
    private final JdbcCourseGradeSummaryRepository courseGradeSummaryRepository;
    private final JdbcGradeAnalysisSnapshotRepository gradeAnalysisSnapshotRepository;
    private final JdbcGradeChangeLogRepository gradeChangeLogRepository;

    @Autowired
    GradeItemMigrationTest(
            JdbcGradeItemRepository repository,
            JdbcGradeRecordRepository gradeRecordRepository,
            JdbcCourseGradeSummaryRepository courseGradeSummaryRepository,
            JdbcGradeAnalysisSnapshotRepository gradeAnalysisSnapshotRepository,
            JdbcGradeChangeLogRepository gradeChangeLogRepository
    ) {
        this.repository = repository;
        this.gradeRecordRepository = gradeRecordRepository;
        this.courseGradeSummaryRepository = courseGradeSummaryRepository;
        this.gradeAnalysisSnapshotRepository = gradeAnalysisSnapshotRepository;
        this.gradeChangeLogRepository = gradeChangeLogRepository;
    }

    @Test
    void gradeItemMigrationSupportsExecutableRepositoryPersistence() {
        LocalDateTime now = LocalDateTime.now();
        GradeItem saved = repository.save(new GradeItem(
                0L,
                101L,
                "实验一",
                SourceType.LAB,
                301L,
                new BigDecimal("100.00"),
                new BigDecimal("0.4000"),
                true,
                true,
                1,
                501L,
                false,
                now,
                now
        ));

        assertThat(saved.id()).isPositive();
        assertThat(repository.findById(saved.id())).contains(saved);
        assertThat(repository.findByCourseId(101L)).containsExactly(saved);
    }

    @Test
    void gradeItemMigrationAllowsRepeatedRecreateAndSoftDeleteWithSameName() {
        LocalDateTime now = LocalDateTime.now();
        GradeItem first = repository.save(new GradeItem(
                0L,
                202L,
                "作业一",
                SourceType.HWK,
                401L,
                new BigDecimal("100.00"),
                new BigDecimal("0.3000"),
                true,
                true,
                1,
                501L,
                false,
                now,
                now
        ));
        repository.update(first.disable(now.plusMinutes(1)));

        GradeItem second = repository.save(new GradeItem(
                0L,
                202L,
                "作业一",
                SourceType.HWK,
                402L,
                new BigDecimal("100.00"),
                new BigDecimal("0.3000"),
                true,
                true,
                2,
                501L,
                false,
                now.plusMinutes(2),
                now.plusMinutes(2)
        ));

        GradeItem deletedSecond = repository.update(second.disable(now.plusMinutes(3)));

        assertThat(deletedSecond.deleted()).isTrue();
        assertThat(repository.findByCourseId(202L)).isEmpty();
    }

    @Test
    void gradeRecordMigrationSupportsExecutableRecordAndSummaryPersistence() {
        LocalDateTime now = LocalDateTime.now();
        GradeRecord record = gradeRecordRepository.upsert(new GradeRecord(
                0L,
                303L,
                601L,
                1L,
                SourceType.LAB,
                301L,
                new BigDecimal("90.00"),
                new BigDecimal("36.00"),
                GradeStatus.SCORED,
                PublishStatus.UNPUBLISHED,
                null,
                now,
                now,
                null,
                now,
                now
        ));
        CourseGradeSummary summary = courseGradeSummaryRepository.upsert(new CourseGradeSummary(
                0L,
                303L,
                601L,
                new BigDecimal("84.00"),
                FinalStatus.CALCULATED,
                PublishStatus.UNPUBLISHED,
                0L,
                null,
                now,
                now
        ));

        assertThat(record.id()).isPositive();
        assertThat(gradeRecordRepository.findByCourseId(303L)).containsExactly(record);
        assertThat(summary.id()).isPositive();
        assertThat(courseGradeSummaryRepository.findByCourseId(303L)).containsExactly(summary);
    }

    @Test
    void gradeChangeLogMigrationSupportsAdjustmentTracePersistence() {
        LocalDateTime now = LocalDateTime.now();
        GradeChangeLog saved = gradeChangeLogRepository.save(new GradeChangeLog(
                0L,
                404L,
                601L,
                1L,
                "RECORD_ADJUST",
                new BigDecimal("90.00"),
                new BigDecimal("95.00"),
                "复核测试用例后修正",
                501L,
                now
        ));

        assertThat(saved.id()).isPositive();
        assertThat(gradeChangeLogRepository.findByCourseId(404L, 601L, null, 1, 20))
                .singleElement()
                .satisfies(actual -> {
                    assertThat(actual.id()).isEqualTo(saved.id());
                    assertThat(actual.courseId()).isEqualTo(saved.courseId());
                    assertThat(actual.studentId()).isEqualTo(saved.studentId());
                    assertThat(actual.gradeItemId()).isEqualTo(saved.gradeItemId());
                    assertThat(actual.changeType()).isEqualTo(saved.changeType());
                    assertThat(actual.oldValue()).isEqualByComparingTo(saved.oldValue());
                    assertThat(actual.newValue()).isEqualByComparingTo(saved.newValue());
                    assertThat(actual.reason()).isEqualTo(saved.reason());
                    assertThat(actual.operatorId()).isEqualTo(saved.operatorId());
                    assertThat(Duration.between(saved.createdAt(), actual.createdAt()).abs())
                            .isLessThanOrEqualTo(Duration.ofNanos(1_000));
                });
        assertThat(gradeChangeLogRepository.countByCourseId(404L, 601L, null)).isEqualTo(1);
    }

    @Test
    void gradeAnalysisMigrationSupportsSnapshotPersistence() {
        LocalDateTime now = LocalDateTime.now();
        GradeAnalysisSnapshot saved = gradeAnalysisSnapshotRepository.save(new GradeAnalysisSnapshot(
                0L,
                505L,
                "COURSE_TOTAL",
                null,
                now.minusMinutes(1),
                "6a6d0f3f657c0f61b92f7fd105a149781039605365c671414bbc27f6536fa72e",
                new BigDecimal("78.00"),
                new BigDecimal("92.00"),
                new BigDecimal("58.00"),
                new BigDecimal("0.6667"),
                new BigDecimal("0.7500"),
                "[{\"label\":\"0-59\",\"count\":1}]",
                501L,
                now
        ));

        assertThat(saved.id()).isPositive();
        assertThat(gradeAnalysisSnapshotRepository.findLatest(505L, "COURSE_TOTAL", null))
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.id()).isEqualTo(saved.id());
                    assertThat(snapshot.courseId()).isEqualTo(saved.courseId());
                    assertThat(snapshot.targetType()).isEqualTo(saved.targetType());
                    assertThat(snapshot.gradeItemId()).isNull();
                    assertThat(snapshot.sourceFingerprint()).isEqualTo(saved.sourceFingerprint());
                    assertThat(snapshot.averageScore()).isEqualByComparingTo(saved.averageScore());
                    assertThat(snapshot.maxScore()).isEqualByComparingTo(saved.maxScore());
                    assertThat(snapshot.minScore()).isEqualByComparingTo(saved.minScore());
                    assertThat(snapshot.passRate()).isEqualByComparingTo(saved.passRate());
                    assertThat(snapshot.completionRate()).isEqualByComparingTo(saved.completionRate());
                    assertThat(snapshot.distributionJson()).isEqualTo(saved.distributionJson());
                    assertThat(snapshot.generatedBy()).isEqualTo(saved.generatedBy());
                    assertThat(Duration.between(saved.sourceDataTime(), snapshot.sourceDataTime()).abs())
                            .isLessThanOrEqualTo(Duration.ofNanos(1_000));
                    assertThat(Duration.between(saved.generatedAt(), snapshot.generatedAt()).abs())
                            .isLessThanOrEqualTo(Duration.ofNanos(1_000));
                });
    }
}
