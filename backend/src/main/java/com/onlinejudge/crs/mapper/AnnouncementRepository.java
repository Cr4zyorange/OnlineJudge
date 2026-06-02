package com.onlinejudge.crs.mapper;

import com.onlinejudge.crs.domain.Announcement;
import com.onlinejudge.crs.domain.dto.AnnouncementRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class AnnouncementRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Announcement> mapper = (rs, rowNum) -> new Announcement(
            rs.getLong("id"),
            rs.getLong("course_id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getBoolean("is_top"),
            rs.getLong("publisher_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public AnnouncementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Announcement insert(Long courseId, AnnouncementRequest request, Long publisherId, String content) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO crs_announcement (course_id, title, content, is_top, publisher_id)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setString(2, request.title().trim());
            ps.setString(3, content);
            ps.setBoolean(4, Boolean.TRUE.equals(request.isTop()));
            ps.setLong(5, publisherId);
            return ps;
        }, keyHolder);
        return findById(courseId, generatedId(keyHolder)).orElseThrow();
    }

    public List<Announcement> listByCourse(Long courseId) {
        return jdbcTemplate.query("""
                SELECT * FROM crs_announcement
                 WHERE course_id = ? AND is_deleted = FALSE
                 ORDER BY is_top DESC, created_at DESC, id DESC
                """, mapper, courseId);
    }

    public Optional<Announcement> findById(Long courseId, Long announcementId) {
        List<Announcement> announcements = jdbcTemplate.query("""
                SELECT * FROM crs_announcement
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, mapper, courseId, announcementId);
        return announcements.stream().findFirst();
    }

    public Announcement update(Long courseId, Long announcementId, AnnouncementRequest request, String content) {
        jdbcTemplate.update("""
                UPDATE crs_announcement
                   SET title = ?, content = ?, is_top = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, request.title().trim(), content, Boolean.TRUE.equals(request.isTop()), courseId, announcementId);
        return findById(courseId, announcementId).orElseThrow();
    }

    public Announcement updateTop(Long courseId, Long announcementId, boolean top) {
        jdbcTemplate.update("""
                UPDATE crs_announcement
                   SET is_top = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, top, courseId, announcementId);
        return findById(courseId, announcementId).orElseThrow();
    }

    public void delete(Long courseId, Long announcementId) {
        jdbcTemplate.update("""
                UPDATE crs_announcement
                   SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, courseId, announcementId);
    }

    private Long generatedId(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().isEmpty()) {
            throw new IllegalStateException("No generated key returned");
        }
        Object value = keyHolder.getKeyList().getFirst().get("id");
        if (value == null) {
            value = keyHolder.getKeyList().getFirst().values().iterator().next();
        }
        return ((Number) value).longValue();
    }
}
