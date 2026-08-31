package com.onlinejudge.assessmentservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabExperimentServiceTest {
    @Test
    void closeRejectsAConditionalUpdateLostToConcurrentScoreRelease() {
        JdbcTemplate jdbc = new LostUpdateJdbcTemplate(dataSource());
        jdbc.execute("""
                CREATE TABLE assessment_lab_experiment (
                    id BIGINT PRIMARY KEY, course_id VARCHAR(80), title VARCHAR(120), status VARCHAR(32),
                    deadline TIMESTAMP, max_score DECIMAL(8,2), auto_evaluate BOOLEAN, created_at TIMESTAMP,
                    evaluation_mode VARCHAR(32), report_required BOOLEAN, published_at TIMESTAMP, deleted BOOLEAN
                )
                """);
        jdbc.update("""
                INSERT INTO assessment_lab_experiment
                (id, course_id, title, status, deadline, max_score, auto_evaluate, created_at, evaluation_mode, report_required, published_at, deleted)
                VALUES (314, 'course-314', 'race', 'PUBLISHED', CURRENT_TIMESTAMP, 100, TRUE, CURRENT_TIMESTAMP, 'CODE', FALSE, CURRENT_TIMESTAMP, FALSE)
                """);
        LabExperimentService service = new LabExperimentService(jdbc, null);

        assertThatThrownBy(() -> service.close(314L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LAB lifecycle changed concurrently");
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:lab-lifecycle-race;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static final class LostUpdateJdbcTemplate extends JdbcTemplate {
        private LostUpdateJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE assessment_lab_experiment SET status = 'CLOSED'")) return 0;
            return super.update(sql, args);
        }
    }
}
