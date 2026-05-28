package com.onlinejudge.crs.mapper;

import com.onlinejudge.crs.domain.Chapter;
import com.onlinejudge.crs.domain.dto.ChapterCreateRequest;
import com.onlinejudge.crs.domain.dto.ChapterUpdateRequest;
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
public class ChapterRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Chapter> mapper = (rs, rowNum) -> new Chapter(
            rs.getLong("id"),
            rs.getLong("course_id"),
            (Long) rs.getObject("parent_id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getInt("order_num"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public ChapterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Chapter insert(Long courseId, ChapterCreateRequest request, int orderNum) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO crs_chapter (course_id, parent_id, title, content, order_num)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setObject(2, request.parentId());
            ps.setString(3, request.title().trim());
            ps.setString(4, normalizeContent(request.content()));
            ps.setInt(5, orderNum);
            return ps;
        }, keyHolder);
        return findById(courseId, generatedId(keyHolder)).orElseThrow();
    }

    public Chapter update(Long courseId, Long chapterId, ChapterUpdateRequest request, int orderNum) {
        jdbcTemplate.update("""
                UPDATE crs_chapter
                   SET parent_id = ?, title = ?, content = ?, order_num = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND course_id = ? AND is_deleted = FALSE
                """, request.parentId(), request.title().trim(), normalizeContent(request.content()), orderNum, chapterId, courseId);
        return findById(courseId, chapterId).orElseThrow();
    }

    public Optional<Chapter> findById(Long courseId, Long chapterId) {
        List<Chapter> chapters = jdbcTemplate.query("""
                SELECT * FROM crs_chapter
                 WHERE id = ? AND course_id = ? AND is_deleted = FALSE
                """, mapper, chapterId, courseId);
        return chapters.stream().findFirst();
    }

    public List<Chapter> listByCourse(Long courseId) {
        return jdbcTemplate.query("""
                SELECT * FROM crs_chapter
                 WHERE course_id = ? AND is_deleted = FALSE
                 ORDER BY COALESCE(parent_id, 0), order_num, id
                """, mapper, courseId);
    }

    public boolean hasActiveChildren(Long courseId, Long chapterId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM crs_chapter
                 WHERE course_id = ? AND parent_id = ? AND is_deleted = FALSE
                """, Long.class, courseId, chapterId);
        return count != null && count > 0;
    }

    public int nextOrder(Long courseId, Long parentId) {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(order_num), 0) + 1 FROM crs_chapter
                 WHERE course_id = ? AND ((? IS NULL AND parent_id IS NULL) OR parent_id = ?) AND is_deleted = FALSE
                """, Integer.class, courseId, parentId, parentId);
        return value == null ? 1 : value;
    }

    public void deleteWithDescendants(Long courseId, Long chapterId) {
        for (Chapter child : listByCourse(courseId).stream().filter(chapter -> chapterId.equals(chapter.parentId())).toList()) {
            deleteWithDescendants(courseId, child.id());
        }
        jdbcTemplate.update("""
                UPDATE crs_chapter
                   SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND course_id = ? AND is_deleted = FALSE
                """, chapterId, courseId);
    }

    private String normalizeContent(String content) {
        return content == null || content.isBlank() ? null : content.trim();
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
