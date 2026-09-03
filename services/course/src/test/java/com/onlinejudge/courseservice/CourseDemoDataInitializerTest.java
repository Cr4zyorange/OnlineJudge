package com.onlinejudge.courseservice;

import com.onlinejudge.courseservice.config.CourseDemoDataInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Issue #396: demo course data must appear on a clean database and never duplicate on restart. */
class CourseDemoDataInitializerTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void schema() {
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:course_demo_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema-course.sql"));
        populator.execute(h2);
        jdbc = new JdbcTemplate(h2);
    }

    @Test
    void seedsDemoCourseAndMembersWhenCourseTableIsEmpty() {
        new CourseDemoDataInitializer(jdbc).seedIfEmpty();

        Map<String, Object> course = jdbc.queryForMap(
                "SELECT id, course_name, teacher_id, enrollment_mode, status FROM crs_course WHERE id = 9501");
        assertEquals(9501L, ((Number) course.get("id")).longValue());
        assertEquals("软件工程实训演示课程", course.get("course_name"));
        assertEquals(2L, ((Number) course.get("teacher_id")).longValue());
        assertEquals("PUBLISHED", course.get("status"));

        Integer members = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crs_course_member WHERE course_id = 9501 AND join_status = 'ACTIVE'",
                Integer.class);
        assertEquals(2, members);
        Integer teacher = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crs_course_member WHERE course_id = 9501 AND user_id = 2 AND role = 'TEACHER'",
                Integer.class);
        assertEquals(1, teacher);
        Integer student = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crs_course_member WHERE course_id = 9501 AND user_id = 1 AND role = 'STUDENT'",
                Integer.class);
        assertEquals(1, student);
    }

    @Test
    void skipsSeedingWhenAnyCourseAlreadyExists() {
        jdbc.update("INSERT INTO crs_course (id, course_name, teacher_id, status) VALUES (1, '既有课程', 77, 'PUBLISHED')");

        new CourseDemoDataInitializer(jdbc).seedIfEmpty();

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM crs_course", Integer.class);
        assertEquals(1, total);
        assertFalse(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crs_course WHERE id = 9501", Integer.class) > 0,
                "demo course must not be inserted when the course table already holds data");
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crs_course_member", Integer.class) == 0,
                "no demo members may be inserted when the course table already holds data");
    }
}
