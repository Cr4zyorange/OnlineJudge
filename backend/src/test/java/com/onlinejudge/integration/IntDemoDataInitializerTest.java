package com.onlinejudge.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:int_demo_data;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.demo-data.enabled=true"
})
class IntDemoDataInitializerTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void intDemoDataCoversLoginCourseLearningLabHomeworkGradeAndNotifications() {
        Long studentId = userId("student001");
        Long teacherId = userId("teacher001");

        assertThat(studentId).isNotNull();
        assertThat(teacherId).isNotNull();

        assertThat(count("""
                SELECT COUNT(*)
                FROM crs_course course
                JOIN crs_course_member teacher
                  ON teacher.course_id = course.id
                 AND teacher.user_id = ?
                 AND teacher.role = 'TEACHER'
                 AND teacher.join_status = 'ACTIVE'
                JOIN crs_course_member student
                  ON student.course_id = course.id
                 AND student.user_id = ?
                 AND student.role = 'STUDENT'
                 AND student.join_status = 'ACTIVE'
                WHERE course.id = 9501
                  AND course.course_name = '数据结构全流程演示课'
                  AND course.status = 'ACTIVE'
                """, teacherId, studentId)).isEqualTo(1);

        assertThat(count("SELECT COUNT(*) FROM crs_chapter WHERE id = 950101 AND course_id = 9501 AND visible_status = 1"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM crs_resource WHERE id = 950102 AND course_id = 9501 AND chapter_id = 950101"))
                .isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*)
                FROM lrn_learning_progress
                WHERE user_id = ?
                  AND course_id = 9501
                  AND source_module = 'CRS'
                  AND source_id = 950102
                  AND status = 'COMPLETED'
                """, studentId)).isEqualTo(1);

        assertThat(count("""
                SELECT COUNT(*)
                FROM lab_experiment lab
                JOIN lab_submission submission ON submission.lab_id = lab.id
                JOIN lab_evaluation evaluation ON evaluation.submission_id = submission.id
                JOIN lab_score score ON score.submission_id = submission.id
                WHERE lab.id = 950201
                  AND lab.course_id = 9501
                  AND lab.status = 'SCORE_PUBLISHED'
                  AND submission.student_id = ?
                  AND submission.evaluation_status = 'ACCEPTED'
                  AND evaluation.status = 'ACCEPTED'
                  AND score.final_score = 92
                """, studentId)).isEqualTo(1);

        assertThat(count("""
                SELECT COUNT(*)
                FROM t_hwk_homework homework
                JOIN t_hwk_submission submission ON submission.homework_id = homework.id
                JOIN t_hwk_evaluation evaluation ON evaluation.submission_id = submission.id
                WHERE homework.id = 950301
                  AND homework.course_id = 9501
                  AND homework.status = 'SCORE_PUBLISHED'
                  AND submission.student_id = ?
                  AND submission.review_status = 'REVIEWED'
                  AND submission.final_score = 88.00
                  AND evaluation.status = 'ACCEPTED'
                """, studentId)).isEqualTo(1);

        assertThat(count("""
                SELECT COUNT(*)
                FROM t_grade_item item
                JOIN t_grade_record record ON record.grade_item_id = item.id
                WHERE item.course_id = 9501
                  AND item.enabled = TRUE
                  AND record.student_id = ?
                  AND record.publish_status = 'PUBLISHED'
                """, studentId)).isEqualTo(2);

        BigDecimal finalScore = jdbcTemplate.queryForObject("""
                SELECT final_score
                FROM t_course_grade_summary
                WHERE course_id = 9501
                  AND student_id = ?
                  AND final_status = 'CALCULATED'
                  AND publish_status = 'PUBLISHED'
                """, BigDecimal.class, studentId);
        assertThat(finalScore).isEqualByComparingTo("89.60");

        assertThat(count("""
                SELECT COUNT(*)
                FROM lrn_notification
                WHERE user_id = ?
                  AND course_id = 9501
                  AND type IN ('EXPERIMENT', 'HOMEWORK', 'GRADE')
                  AND source_module IN ('LAB', 'HWK', 'GRD')
                  AND action_url IS NOT NULL
                """, studentId)).isGreaterThanOrEqualTo(3);

        assertThat(count("""
                SELECT COUNT(*)
                FROM lrn_learning_task
                WHERE user_id = ?
                  AND course_id = 9501
                  AND source_module IN ('LAB', 'HWK')
                  AND status IN ('COMPLETED', 'IN_PROGRESS')
                """, studentId)).isEqualTo(2);
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM t_auth_user WHERE username = ?", Long.class, username);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
