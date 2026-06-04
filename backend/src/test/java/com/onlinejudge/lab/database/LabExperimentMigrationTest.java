package com.onlinejudge.lab.database;

import com.onlinejudge.lab.domain.LabEvaluationMode;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabTestcase;
import com.onlinejudge.lab.repository.JdbcLabExperimentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lab_experiment_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcLabExperimentRepository.class)
@Sql(scripts = {
        "file:../database/migrations/20260525_02_create_lab_experiment.sql",
        "file:../database/migrations/20260526_01_create_lab_submission.sql",
        "file:../database/migrations/20260604_01_create_lab_report.sql"
})
class LabExperimentMigrationTest {
    private final LabExperimentRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LabExperimentMigrationTest(JdbcLabExperimentRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationSupportsSavingExperimentWithOrderedTestcases() {
        LocalDateTime now = LocalDateTime.now();
        LabExperiment saved = repository.save(new LabExperiment(
                0L,
                101L,
                null,
                "实验一",
                "实现链表插入与删除",
                LabExperimentStatus.DRAFT,
                now.plusDays(7),
                100,
                List.of(11L, 12L),
                "java,python",
                LabEvaluationMode.DOCKER_IO,
                true,
                false,
                60000,
                262144,
                501L,
                false,
                now,
                now,
                List.of(
                        new LabTestcase(0L, 0L, "1 2", "3", 30, true, 1000, 65536, 1, false, now, now),
                        new LabTestcase(0L, 0L, "2 3", "5", 70, false, 1000, 65536, 2, false, now, now)
                )
        ));

        assertThat(saved.id()).isPositive();
        assertThat(saved.testcases()).hasSize(2);
        assertThat(saved.testcases()).extracting(LabTestcase::labId).containsOnly(saved.id());
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void migrationSupportsSoftDeletingDraftExperimentWithoutBreakingListQuery() {
        LocalDateTime now = LocalDateTime.now();
        LabExperiment first = repository.save(new LabExperiment(
                0L,
                202L,
                null,
                "草稿实验",
                "待删除实验",
                LabExperimentStatus.DRAFT,
                now.plusDays(5),
                100,
                List.of(),
                "java",
                LabEvaluationMode.DOCKER_IO,
                true,
                false,
                60000,
                262144,
                501L,
                false,
                now,
                now,
                List.of()
        ));
        repository.update(first.delete(now.plusMinutes(1)));

        LabExperiment second = repository.save(new LabExperiment(
                0L,
                202L,
                null,
                "已发布实验",
                "可见实验",
                LabExperimentStatus.PUBLISHED,
                now.plusDays(6),
                100,
                List.of(),
                "java",
                LabEvaluationMode.DOCKER_IO,
                true,
                false,
                60000,
                262144,
                501L,
                false,
                now.plusMinutes(2),
                now.plusMinutes(2),
                List.of()
        ));

        assertThat(repository.findByCourseId(202L, null)).containsExactly(second);
        assertThat(repository.findByCourseId(202L, LabExperimentStatus.DRAFT)).isEmpty();
        assertThat(repository.findByCourseId(202L, LabExperimentStatus.PUBLISHED)).containsExactly(second);
    }

    @Test
    void migrationSupportsPersistingVersionedLabReports() {
        LocalDateTime now = LocalDateTime.now();
        LabExperiment saved = repository.save(new LabExperiment(
                0L,
                303L,
                null,
                "报告实验",
                "包含实验报告的迁移验证",
                LabExperimentStatus.PUBLISHED,
                now.plusDays(7),
                100,
                List.of(),
                "python",
                LabEvaluationMode.DOCKER_IO,
                true,
                true,
                60000,
                262144,
                501L,
                false,
                now,
                now,
                List.of()
        ));

        jdbcTemplate.update(
                """
                INSERT INTO lab_submission
                    (lab_id, student_id, code_content, file_id, language, submit_status, evaluation_status,
                     final_score, auto_score, version, is_final, submitted_at, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                saved.id(),
                601L,
                "print('lab report')",
                null,
                "python",
                "SUBMITTED",
                "NONE",
                null,
                null,
                1,
                true,
                java.sql.Timestamp.valueOf(now),
                java.sql.Timestamp.valueOf(now),
                java.sql.Timestamp.valueOf(now),
                false
        );
        Long submissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM lab_submission WHERE lab_id = ? AND student_id = ?",
                Long.class,
                saved.id(),
                601L
        );

        jdbcTemplate.update(
                """
                INSERT INTO lab_report
                    (lab_id, student_id, submission_id, file_id, file_name, file_type, file_size, version,
                     submit_status, score, comment, submitted_at, scored_by, scored_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                saved.id(),
                601L,
                submissionId,
                "stored-report-1",
                "report-v1.pdf",
                "PDF",
                1024L,
                1,
                "SUBMITTED",
                95,
                "报告完整",
                java.sql.Timestamp.valueOf(now),
                501L,
                java.sql.Timestamp.valueOf(now.plusHours(1)),
                java.sql.Timestamp.valueOf(now),
                java.sql.Timestamp.valueOf(now.plusHours(1))
        );

        Integer reportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_report WHERE lab_id = ? AND student_id = ?",
                Integer.class,
                saved.id(),
                601L
        );
        Integer storedVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM lab_report WHERE lab_id = ? AND student_id = ?",
                Integer.class,
                saved.id(),
                601L
        );
        String storedType = jdbcTemplate.queryForObject(
                "SELECT file_type FROM lab_report WHERE lab_id = ? AND student_id = ?",
                String.class,
                saved.id(),
                601L
        );

        assertThat(reportCount).isEqualTo(1);
        assertThat(storedVersion).isEqualTo(1);
        assertThat(storedType).isEqualTo("PDF");
    }
}
