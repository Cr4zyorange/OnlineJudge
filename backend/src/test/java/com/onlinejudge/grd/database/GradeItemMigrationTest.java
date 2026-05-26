package com.onlinejudge.grd.database;

import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.grd.repository.JdbcCourseGradeSummaryRepository;
import com.onlinejudge.grd.repository.JdbcGradeItemRepository;
import com.onlinejudge.grd.repository.JdbcGradeRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grade_item_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JdbcGradeItemRepository.class,
        JdbcGradeRecordRepository.class,
        JdbcCourseGradeSummaryRepository.class
})
@Sql(scripts = "file:../database/migrations/20260525_01_create_grd_grade_item.sql")
class GradeItemMigrationTest {
    private final JdbcGradeItemRepository repository;
    private final JdbcGradeRecordRepository gradeRecordRepository;
    private final JdbcCourseGradeSummaryRepository courseGradeSummaryRepository;

    @Autowired
    GradeItemMigrationTest(
            JdbcGradeItemRepository repository,
            JdbcGradeRecordRepository gradeRecordRepository,
            JdbcCourseGradeSummaryRepository courseGradeSummaryRepository
    ) {
        this.repository = repository;
        this.gradeRecordRepository = gradeRecordRepository;
        this.courseGradeSummaryRepository = courseGradeSummaryRepository;
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
}
