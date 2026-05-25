package com.onlinejudge.grd.database;

import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.grd.repository.JdbcGradeItemRepository;
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
@Import(JdbcGradeItemRepository.class)
@Sql(scripts = "file:../database/migrations/20260525_01_create_grd_grade_item.sql")
class GradeItemMigrationTest {
    private final JdbcGradeItemRepository repository;

    @Autowired
    GradeItemMigrationTest(JdbcGradeItemRepository repository) {
        this.repository = repository;
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
}
