package com.onlinejudge.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = com.onlinejudge.identityservice.IdentityServiceApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:auth_seed_disabled;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "onlinejudge.auth.seed-data-enabled=false"
        }
)
class AuthSeedDataDisabledTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void disabledSeedLeavesAuthUsersEmpty() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_auth_user", Integer.class);
        assertThat(count).isZero();
    }
}
