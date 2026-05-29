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
@Sql(scripts = "file:../database/migrations/20260525_02_create_lab_experiment.sql")
class LabExperimentMigrationTest {
    private final LabExperimentRepository repository;

    @Autowired
    LabExperimentMigrationTest(JdbcLabExperimentRepository repository) {
        this.repository = repository;
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
}
