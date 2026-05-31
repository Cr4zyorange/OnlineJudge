package com.onlinejudge.crs.mapper;

import com.onlinejudge.crs.domain.CourseResource;
import com.onlinejudge.crs.domain.ResourceType;
import com.onlinejudge.crs.domain.ResourceVisibility;
import com.onlinejudge.crs.domain.dto.ResourceUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ResourceRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CourseResource> mapper = (rs, rowNum) -> new CourseResource(
            rs.getLong("id"),
            rs.getLong("course_id"),
            (Long) rs.getObject("chapter_id"),
            rs.getString("resource_name"),
            ResourceType.valueOf(rs.getString("resource_type")),
            ResourceVisibility.valueOf(rs.getString("visibility")),
            nullableDateTime(rs.getObject("publish_at")),
            rs.getString("storage_key"),
            rs.getString("original_filename"),
            rs.getString("content_type"),
            rs.getLong("file_size"),
            rs.getLong("upload_user_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public ResourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CourseResource insert(Long courseId, Long chapterId, String name, ResourceType resourceType,
                                 ResourceVisibility visibility, LocalDateTime publishAt, String storageKey,
                                 String originalFilename, String contentType, long fileSize, Long uploadUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO crs_resource (
                        course_id, chapter_id, resource_name, resource_type, visibility, publish_at,
                        storage_key, original_filename, content_type, file_size, upload_user_id
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setObject(2, chapterId);
            ps.setString(3, name);
            ps.setString(4, resourceType.name());
            ps.setString(5, visibility.name());
            ps.setObject(6, publishAt);
            ps.setString(7, storageKey);
            ps.setString(8, originalFilename);
            ps.setString(9, contentType);
            ps.setLong(10, fileSize);
            ps.setLong(11, uploadUserId);
            return ps;
        }, keyHolder);
        return findById(courseId, generatedId(keyHolder)).orElseThrow();
    }

    public List<CourseResource> listByCourse(Long courseId, boolean teacherView) {
        if (teacherView) {
            return jdbcTemplate.query("""
                    SELECT * FROM crs_resource
                     WHERE course_id = ? AND is_deleted = FALSE
                     ORDER BY COALESCE(publish_at, created_at) DESC, id DESC
                    """, mapper, courseId);
        }
        return jdbcTemplate.query("""
                SELECT * FROM crs_resource
                 WHERE course_id = ?
                   AND is_deleted = FALSE
                   AND visibility = 'STUDENT'
                   AND (publish_at IS NULL OR publish_at <= CURRENT_TIMESTAMP)
                 ORDER BY COALESCE(publish_at, created_at) DESC, id DESC
                """, mapper, courseId);
    }

    public Optional<CourseResource> findById(Long courseId, Long resourceId) {
        List<CourseResource> resources = jdbcTemplate.query("""
                SELECT * FROM crs_resource
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, mapper, courseId, resourceId);
        return resources.stream().findFirst();
    }

    public CourseResource update(Long courseId, Long resourceId, ResourceUpdateRequest request) {
        jdbcTemplate.update("""
                UPDATE crs_resource
                   SET chapter_id = ?, resource_name = ?, resource_type = ?, visibility = ?,
                       publish_at = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, request.chapterId(), request.name().trim(), request.resourceType().name(),
                request.visibility().name(), request.publishAt(), courseId, resourceId);
        return findById(courseId, resourceId).orElseThrow();
    }

    public void delete(Long courseId, Long resourceId) {
        jdbcTemplate.update("""
                UPDATE crs_resource
                   SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, courseId, resourceId);
    }

    private LocalDateTime nullableDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((java.sql.Timestamp) value).toLocalDateTime();
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
