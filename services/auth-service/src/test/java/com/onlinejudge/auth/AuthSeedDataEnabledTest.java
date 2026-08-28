package com.onlinejudge.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = com.onlinejudge.authservice.AuthServiceApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:auth_seed_enabled;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "onlinejudge.auth.seed-data-enabled=true"
        }
)
class AuthSeedDataEnabledTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void enabledSeedCreatesDocumentedAccountsWithExpectedRoles() {
        Map<String, String> expectedRoles = Map.of(
                "student001", "STUDENT",
                "teacher001", "TEACHER",
                "admin001", "ADMIN"
        );

        Map<String, String> actualRoles = jdbcTemplate.query(
                """
                        SELECT u.username, r.role_code
                        FROM t_auth_user u
                        JOIN t_auth_user_role ur ON ur.user_id = u.user_id
                        JOIN t_auth_role r ON r.role_id = ur.role_id
                        WHERE u.username IN ('student001', 'teacher001', 'admin001')
                        """,
                resultSet -> {
                    var roles = new java.util.HashMap<String, String>();
                    while (resultSet.next()) {
                        roles.put(resultSet.getString("username"), resultSet.getString("role_code"));
                    }
                    return roles;
                }
        );

        assertThat(actualRoles).containsExactlyInAnyOrderEntriesOf(expectedRoles);
    }
}
