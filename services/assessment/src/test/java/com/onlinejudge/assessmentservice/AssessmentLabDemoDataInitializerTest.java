package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.config.AssessmentLabDemoDataInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import org.h2.jdbcx.JdbcDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Issue #396: LAB demo experiments must mirror the monolith demo on a clean Assessment database. */
class AssessmentLabDemoDataInitializerTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void schema() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:assessment_demo_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema-assessment.sql"));
        populator.execute(h2);
        jdbc = new JdbcTemplate(h2);
    }

    @Test
    void seedsTwoDemoExperimentsWithEvaluatedSubmissionWhenLabTableIsEmpty() {
        new AssessmentLabDemoDataInitializer(jdbc).seedIfEmpty();

        Integer experiments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_lab_experiment WHERE course_id = '9501'", Integer.class);
        assertEquals(2, experiments);
        Map<String, Object> graded = jdbc.queryForMap(
                "SELECT id, status, auto_evaluate FROM assessment_lab_experiment WHERE id = 950201");
        assertEquals(950201L, ((Number) graded.get("id")).longValue());
        assertEquals("SCORE_PUBLISHED", graded.get("status"));
        assertEquals(Boolean.TRUE, graded.get("auto_evaluate"));
        Map<String, Object> open = jdbc.queryForMap(
                "SELECT status FROM assessment_lab_experiment WHERE id = 950211");
        assertEquals("PUBLISHED", open.get("status"));

        Integer testcases = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_lab_testcase WHERE lab_id = 950201", Integer.class);
        assertEquals(1, testcases);

        Map<String, Object> submission = jdbc.queryForMap(
                "SELECT student_id, submit_status, final_score FROM assessment_lab_submission WHERE lab_id = 950201");
        assertEquals("1", submission.get("student_id"));
        assertEquals("SUBMITTED", submission.get("submit_status"));
        assertEquals(0, java.math.BigDecimal.valueOf(92).compareTo((java.math.BigDecimal) submission.get("final_score")));

        Integer results = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_lab_evaluation_result WHERE passed = TRUE", Integer.class);
        assertEquals(1, results);
        Integer scores = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_lab_score WHERE final_score = 92", Integer.class);
        assertEquals(1, scores);

        Integer projections = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_course_member_projection "
                        + "WHERE course_id = '9501' AND membership_status = 'ACTIVE'", Integer.class);
        assertEquals(2, projections);
    }

    @Test
    void skipsSeedingWhenAnyLabExperimentAlreadyExists() {
        jdbc.update("""
                INSERT INTO assessment_lab_experiment
                  (id, course_id, title, description, status, deadline, max_score,
                   allowed_languages, auto_evaluate, created_by, created_at, updated_at)
                VALUES (1, 'other', '既有实验', 'x', 'PUBLISHED',
                        CURRENT_TIMESTAMP, 100, 'python', FALSE, '9', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        new AssessmentLabDemoDataInitializer(jdbc).seedIfEmpty();

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_experiment", Integer.class);
        assertEquals(1, total);
        assertFalse(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_lab_experiment WHERE id = 950201", Integer.class) > 0,
                "demo experiments must not be inserted when LAB data already exists");
    }
}
