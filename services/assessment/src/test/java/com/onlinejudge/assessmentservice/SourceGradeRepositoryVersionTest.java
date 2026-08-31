package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SourceGradeRepositoryVersionTest {
    @Autowired SourceGradeRepository grades;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_source_grade_snapshot");
        jdbc.update("DELETE FROM assessment_source_grade");
    }

    @Test
    void eachScoredMutationReturnsTheRevisionThatWasAtomicallyPersisted() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        assertThat(grades.upsertScored("HWK", "revision-1", "course-1", "student-1", new BigDecimal("70"), new BigDecimal("100"), now)).isEqualTo(1);
        assertThat(grades.upsertScored("HWK", "revision-1", "course-1", "student-1", new BigDecimal("91"), new BigDecimal("100"), now.plusSeconds(1))).isEqualTo(2);
        assertThat(grades.upsertScored("HWK", "revision-1", "course-1", "student-1", new BigDecimal("82"), new BigDecimal("100"), now.plusSeconds(2))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type='HWK' AND source_id='revision-1' AND student_id='student-1'", Long.class)).isEqualTo(3L);
    }

    @Test
    void simultaneousCompletionsGetDistinctStrictlyIncreasingAggregateVersions() throws Exception {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        assertThat(grades.upsertScored("HWK", "concurrent", "course-1", "student-1", new BigDecimal("70"), new BigDecimal("100"), now)).isEqualTo(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Long> first = pool.submit(() -> upsertAfterBarrier(ready, start, new BigDecimal("91"), now.plusSeconds(1)));
            Future<Long> second = pool.submit(() -> upsertAfterBarrier(ready, start, new BigDecimal("82"), now.plusSeconds(2)));
            ready.await();
            start.countDown();
            assertThat(first.get()).isIn(2L, 3L);
            assertThat(second.get()).isIn(2L, 3L);
            assertThat(first.get()).isNotEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject("SELECT source_version FROM assessment_source_grade WHERE source_type='HWK' AND source_id='concurrent' AND student_id='student-1'", Long.class)).isEqualTo(3L);
    }

    private long upsertAfterBarrier(CountDownLatch ready, CountDownLatch start, BigDecimal score, Instant now) throws Exception {
        ready.countDown();
        start.await();
        return grades.upsertScored("HWK", "concurrent", "course-1", "student-1", score, new BigDecimal("100"), now);
    }
}
