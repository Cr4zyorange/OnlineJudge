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
    private Boolean legacyColumnsPresent;

    private final RowMapper<Chapter> mapper = (rs, rowNum) -> new Chapter(
            rs.getLong("id"),
            rs.getLong("course_id"),
            (Long) rs.getObject("parent_id"),
            rs.getString("chapter_name"),
            rs.getInt("sort_order"),
            rs.getString("objective"),
            rs.getInt("visible_status"),
            rs.getInt("chapter_type"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public ChapterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Chapter insert(Long courseId, ChapterCreateRequest request, int sortOrder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            boolean legacyColumns = hasLegacyColumns();
            PreparedStatement ps = connection.prepareStatement(legacyColumns ? """
                    INSERT INTO crs_chapter (
                        course_id, parent_id, chapter_name, sort_order, objective, visible_status, chapter_type,
                        title, content, order_num
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """ : """
                    INSERT INTO crs_chapter (course_id, parent_id, chapter_name, sort_order, objective, visible_status, chapter_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setObject(2, request.parentId());
            ps.setString(3, request.chapterName().trim());
            ps.setInt(4, sortOrder);
            ps.setString(5, normalizeText(request.objective()));
            ps.setInt(6, request.visibleStatus() == null ? 1 : request.visibleStatus());
            ps.setInt(7, request.chapterType() == null ? 1 : request.chapterType());
            if (legacyColumns) {
                ps.setString(8, request.chapterName().trim());
                ps.setString(9, normalizeText(request.objective()));
                ps.setInt(10, sortOrder);
            }
            return ps;
        }, keyHolder);
        return findById(courseId, generatedId(keyHolder)).orElseThrow();
    }

    public Chapter update(Long courseId, Long chapterId, ChapterUpdateRequest request, int sortOrder) {
        if (hasLegacyColumns()) {
            jdbcTemplate.update("""
                    UPDATE crs_chapter
                       SET parent_id = ?, chapter_name = ?, sort_order = ?, objective = ?,
                           visible_status = ?, chapter_type = ?, title = ?, content = ?, order_num = ?,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = ? AND course_id = ? AND is_deleted = FALSE
                    """,
                    request.parentId(),
                    request.chapterName().trim(),
                    sortOrder,
                    normalizeText(request.objective()),
                    request.visibleStatus() == null ? 1 : request.visibleStatus(),
                    request.chapterType() == null ? 1 : request.chapterType(),
                    request.chapterName().trim(),
                    normalizeText(request.objective()),
                    sortOrder,
                    chapterId,
                    courseId);
        } else {
            jdbcTemplate.update("""
                    UPDATE crs_chapter
                       SET parent_id = ?, chapter_name = ?, sort_order = ?, objective = ?,
                           visible_status = ?, chapter_type = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ? AND course_id = ? AND is_deleted = FALSE
                    """,
                    request.parentId(),
                    request.chapterName().trim(),
                    sortOrder,
                    normalizeText(request.objective()),
                    request.visibleStatus() == null ? 1 : request.visibleStatus(),
                    request.chapterType() == null ? 1 : request.chapterType(),
                    chapterId,
                    courseId);
        }
        return findById(courseId, chapterId).orElseThrow();
    }

    public void updateSortOrder(Long courseId, Long chapterId, int sortOrder) {
        if (hasLegacyColumns()) {
            jdbcTemplate.update("""
                    UPDATE crs_chapter
                       SET sort_order = ?, order_num = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ? AND course_id = ? AND is_deleted = FALSE
                    """, sortOrder, sortOrder, chapterId, courseId);
        } else {
            jdbcTemplate.update("""
                    UPDATE crs_chapter
                       SET sort_order = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ? AND course_id = ? AND is_deleted = FALSE
                    """, sortOrder, chapterId, courseId);
        }
    }

    public Optional<Chapter> findById(Long chapterId) {
        List<Chapter> chapters = jdbcTemplate.query("""
                SELECT * FROM crs_chapter
                 WHERE id = ? AND is_deleted = FALSE
                """, mapper, chapterId);
        return chapters.stream().findFirst();
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
                 ORDER BY COALESCE(parent_id, 0), sort_order, id
                """, mapper, courseId);
    }

    public List<Chapter> listSiblings(Long courseId, Long parentId) {
        return jdbcTemplate.query("""
                SELECT * FROM crs_chapter
                 WHERE course_id = ?
                   AND ((? IS NULL AND parent_id IS NULL) OR parent_id = ?)
                   AND is_deleted = FALSE
                 ORDER BY sort_order, id
                """, mapper, courseId, parentId, parentId);
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
                SELECT COALESCE(MAX(sort_order), 0) + 1 FROM crs_chapter
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

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasLegacyColumns() {
        if (legacyColumnsPresent != null) {
            return legacyColumnsPresent;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE LOWER(TABLE_NAME) = 'crs_chapter'
                   AND LOWER(COLUMN_NAME) IN ('title', 'content', 'order_num')
                """, Integer.class);
        legacyColumnsPresent = count != null && count == 3;
        return legacyColumnsPresent;
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
