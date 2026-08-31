package com.onlinejudge.courseservice;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Statement;
import java.util.Locale;

/** Keeps test isolation compatible with the MySQL self-referencing chapter FK. */
final class CourseTestDataCleanup {
    private CourseTestDataCleanup() { }

    static void deleteChapters(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                boolean mysql = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
                if (mysql) statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    statement.executeUpdate("DELETE FROM crs_chapter");
                } finally {
                    if (mysql) statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }
}
