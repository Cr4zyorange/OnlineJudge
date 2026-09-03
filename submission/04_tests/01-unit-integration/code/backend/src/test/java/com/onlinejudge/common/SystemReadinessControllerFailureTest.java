package com.onlinejudge.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:system_readiness_failure;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "onlinejudge.demo-data.enabled=false"
})
@AutoConfigureMockMvc
class SystemReadinessControllerFailureTest {
    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void readinessReturnsServiceUnavailableWithoutLeakingDatabaseDetails() throws Exception {
        doThrow(new DataAccessResourceFailureException(
                "jdbc:mysql://secret-user:secret-password@mysql/onlinejudge"))
                .when(jdbcTemplate).queryForObject("SELECT 1", Integer.class);

        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("503"))
                .andExpect(content().string(not(containsString("UP"))))
                .andExpect(content().string(not(containsString("secret-user"))))
                .andExpect(content().string(not(containsString("secret-password"))))
                .andExpect(content().string(not(containsString("jdbc:mysql"))));
    }
}
