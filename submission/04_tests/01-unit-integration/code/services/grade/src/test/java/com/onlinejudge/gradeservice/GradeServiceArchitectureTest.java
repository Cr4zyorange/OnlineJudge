package com.onlinejudge.gradeservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "grade.rabbit.enabled=false")
class GradeServiceArchitectureTest {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test
    void independentServiceExposesTheExistingGrdApiAndOwnsAllRuntimeTables() {
        assertThat(context.containsBean("gradeItemController")).isTrue();
        assertThat(context.containsBean("gradeRecordController")).isTrue();
        assertThat(context.containsBean("gradeReviewController")).isTrue();
        assertThat(context.containsBean("gradeAnalysisController")).isTrue();

        assertThat(tableExists("T_GRADE_ITEM")).isTrue();
        assertThat(tableExists("T_GRADE_RECORD")).isTrue();
        assertThat(tableExists("T_COURSE_GRADE_SUMMARY")).isTrue();
        assertThat(tableExists("T_GRADE_CALCULATION_BATCH")).isTrue();
        assertThat(tableExists("GRADE_RESULT_TRACE")).isTrue();
        assertThat(tableExists("GRADE_EVENT_OUTBOX")).isTrue();
        assertThat(tableExists("GRADE_EVENT_INBOX")).isTrue();
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                 WHERE UPPER(TABLE_NAME)=?
                """, Integer.class, table);
        return count != null && count == 1;
    }
}
