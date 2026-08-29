package com.onlinejudge.integration.config;

import org.springframework.jdbc.core.JdbcTemplate;

public abstract class AbstractDemoDataSeeder implements DemoDataSeeder {
    protected final JdbcTemplate jdbc;

    protected AbstractDemoDataSeeder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    protected boolean ready(String... tables) {
        for (String table : tables) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME)=LOWER(?)",
                    Integer.class, table);
            if (count == null || count == 0) return false;
        }
        return true;
    }

    protected void insert(long id, String table, String sql, Object... args) {
        insertByCount("SELECT COUNT(*) FROM " + table + " WHERE id=?", new Object[]{id}, sql, args);
    }

    protected void insertByCount(String countSql, Object[] countArgs, String insertSql, Object... insertArgs) {
        Integer count = jdbc.queryForObject(countSql, Integer.class, countArgs);
        if (count == null || count == 0) jdbc.update(insertSql, insertArgs);
    }
}
