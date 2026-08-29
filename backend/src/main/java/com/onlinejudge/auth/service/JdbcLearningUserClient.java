package com.onlinejudge.auth.service;

import com.onlinejudge.integration.learning.LearningUserClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

@Component
public class JdbcLearningUserClient implements LearningUserClient {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningUserClient(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public Map<Long, String> findDisplayNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        Map<Long, String> result = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT user_id, display_name FROM t_auth_user WHERE deleted=FALSE AND user_id IN (" + placeholders + ")",
                (rs, n) -> Map.entry(rs.getLong("user_id"), rs.getString("display_name")), userIds.toArray())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    @Override
    public OptionalLong findUserIdByUsername(String username) {
        return jdbcTemplate.query("SELECT user_id FROM t_auth_user WHERE username=? AND deleted=FALSE",
                (rs, rowNum) -> rs.getLong("user_id"), username).stream().mapToLong(Long::longValue).findFirst();
    }
}
