package com.onlinejudge.crs.mapper;

import com.onlinejudge.crs.domain.*;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.domain.dto.CourseUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Course> courseMapper = (rs, rowNum) -> new Course(
            rs.getLong("id"),
            rs.getString("course_name"),
            rs.getString("description"),
            rs.getLong("teacher_id"),
            rs.getString("semester"),
            rs.getString("category"),
            rs.getString("cover_url"),
            EnrollmentMode.valueOf(rs.getString("enrollment_mode")),
            rs.getString("invite_code"),
            (Integer) rs.getObject("max_students"),
            nullableDate(rs.getObject("start_date")),
            nullableDate(rs.getObject("end_date")),
            CourseStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final RowMapper<CourseMember> memberMapper = (rs, rowNum) -> new CourseMember(
            rs.getLong("id"),
            rs.getLong("course_id"),
            rs.getLong("user_id"),
            CourseMemberRole.valueOf(rs.getString("role")),
            rs.getString("join_method"),
            CourseMemberStatus.valueOf(rs.getString("join_status")),
            (Long) rs.getObject("approved_by"),
            rs.getTimestamp("joined_at") == null ? null : rs.getTimestamp("joined_at").toLocalDateTime()
    );

    public CourseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Course insert(CourseCreateRequest request, Long teacherId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO crs_course (course_name, description, teacher_id, semester, category, cover_url,
                        enrollment_mode, invite_code, max_students, start_date, end_date, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bindCourse(ps, request.name(), request.description(), teacherId, request.semester(), request.category(),
                    request.coverUrl(), defaultEnrollmentMode(request.enrollmentMode()), normalizedInviteCode(request.enrollmentMode(), request.inviteCode()),
                    request.maxStudents(), request.startDate(), request.endDate(), defaultStatus(request.status()));
            return ps;
        }, keyHolder);
        return findById(generatedId(keyHolder)).orElseThrow();
    }

    public Course update(Long courseId, CourseUpdateRequest request) {
        jdbcTemplate.update("""
                UPDATE crs_course
                   SET course_name = ?, description = ?, semester = ?, category = ?, cover_url = ?,
                       enrollment_mode = ?, invite_code = ?, max_students = ?, start_date = ?, end_date = ?,
                       status = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND is_deleted = FALSE
                """, request.name(), request.description(), request.semester(), request.category(), request.coverUrl(),
                defaultEnrollmentMode(request.enrollmentMode()).name(), normalizedInviteCode(request.enrollmentMode(), request.inviteCode()), request.maxStudents(),
                request.startDate(), request.endDate(), defaultStatus(request.status()).name(), courseId);
        return findById(courseId).orElseThrow();
    }

    public Optional<Course> findById(Long courseId) {
        List<Course> courses = jdbcTemplate.query("SELECT * FROM crs_course WHERE id = ? AND is_deleted = FALSE", courseMapper, courseId);
        return courses.stream().findFirst();
    }

    public List<Course> list(String keyword, int page, int size, String scope, Long userId, boolean admin) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String scopeCondition = scopeCondition(scope, admin);
        if (requiresUser(scope, admin)) {
            return jdbcTemplate.query("""
                    SELECT c.* FROM crs_course c
                    JOIN crs_course_member m ON m.course_id = c.id
                     WHERE c.is_deleted = FALSE
                       AND m.user_id = ?
                       AND m.role = 'TEACHER'
                       AND m.join_status = 'ACTIVE'
                       AND m.is_deleted = FALSE
                       AND (c.course_name LIKE ? OR c.category LIKE ? OR c.semester LIKE ?)
                    """ + scopeCondition + """
                     ORDER BY c.created_at DESC
                     LIMIT ? OFFSET ?
                    """, courseMapper, userId, like, like, like, size, (page - 1) * size);
        }
        return jdbcTemplate.query("""
                SELECT c.* FROM crs_course c
                 WHERE c.is_deleted = FALSE
                   AND (c.course_name LIKE ? OR c.category LIKE ? OR c.semester LIKE ?)
                """ + scopeCondition + """
                 ORDER BY c.created_at DESC
                 LIMIT ? OFFSET ?
                """, courseMapper, like, like, like, size, (page - 1) * size);
    }

    public long count(String keyword, String scope, Long userId, boolean admin) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String scopeCondition = scopeCondition(scope, admin);
        if (requiresUser(scope, admin)) {
            return jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM crs_course c
                    JOIN crs_course_member m ON m.course_id = c.id
                     WHERE c.is_deleted = FALSE
                       AND m.user_id = ?
                       AND m.role = 'TEACHER'
                       AND m.join_status = 'ACTIVE'
                       AND m.is_deleted = FALSE
                       AND (c.course_name LIKE ? OR c.category LIKE ? OR c.semester LIKE ?)
                    """ + scopeCondition, Long.class, userId, like, like, like);
        }
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM crs_course c
                 WHERE c.is_deleted = FALSE
                   AND (c.course_name LIKE ? OR c.category LIKE ? OR c.semester LIKE ?)
                """ + scopeCondition, Long.class, like, like, like);
    }

    public void archive(Long courseId) {
        jdbcTemplate.update("UPDATE crs_course SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE", courseId);
    }

    public CourseMember insertMember(Long courseId, Long userId, CourseMemberRole role, CourseMemberStatus status, String method, Long approvedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO crs_course_member (course_id, user_id, role, join_method, join_status, approved_by, joined_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setLong(2, userId);
            ps.setString(3, role.name());
            ps.setString(4, method);
            ps.setString(5, status.name());
            if (approvedBy == null) {
                ps.setObject(6, null);
            } else {
                ps.setLong(6, approvedBy);
            }
            return ps;
        }, keyHolder);
        generatedId(keyHolder);
        return findMember(courseId, userId).orElseThrow();
    }

    public CourseMember updateMemberForJoin(Long courseId, Long userId, CourseMemberStatus status, String method, Long approvedBy) {
        jdbcTemplate.update("""
                UPDATE crs_course_member
                   SET role = 'STUDENT',
                       join_method = ?,
                       join_status = ?,
                       approved_by = ?,
                       joined_at = CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE joined_at END,
                       left_at = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND user_id = ? AND is_deleted = FALSE
                """, method, status.name(), approvedBy, status.name(), courseId, userId);
        return findMember(courseId, userId).orElseThrow();
    }

    public Optional<CourseMember> findMember(Long courseId, Long userId) {
        List<CourseMember> members = jdbcTemplate.query("""
                SELECT * FROM crs_course_member
                 WHERE course_id = ? AND user_id = ? AND is_deleted = FALSE
                """, memberMapper, courseId, userId);
        return members.stream().findFirst();
    }

    public List<CourseMember> listMembers(Long courseId, CourseMemberStatus status) {
        if (status == null) {
            return jdbcTemplate.query("""
                    SELECT * FROM crs_course_member
                     WHERE course_id = ? AND is_deleted = FALSE
                     ORDER BY join_status, role, user_id
                    """, memberMapper, courseId);
        }
        return jdbcTemplate.query("""
                SELECT * FROM crs_course_member
                 WHERE course_id = ? AND join_status = ? AND is_deleted = FALSE
                 ORDER BY role, user_id
                """, memberMapper, courseId, status.name());
    }

    public CourseMember updateMember(Long courseId, Long userId, CourseMemberRole role, CourseMemberStatus status, Long approvedBy) {
        jdbcTemplate.update("""
                UPDATE crs_course_member
                   SET role = ?,
                       join_status = ?,
                       approved_by = CASE WHEN ? = 'ACTIVE' THEN ? ELSE approved_by END,
                       joined_at = CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE joined_at END,
                       left_at = CASE WHEN ? = 'REMOVED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND user_id = ? AND is_deleted = FALSE
                """, role.name(), status.name(), status.name(), approvedBy, status.name(), status.name(), courseId, userId);
        return findMember(courseId, userId).orElseThrow();
    }

    public long memberCount(Long courseId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM crs_course_member
                 WHERE course_id = ? AND join_status = 'ACTIVE' AND is_deleted = FALSE
                """, Long.class, courseId);
    }

    public long activeStudentCount(Long courseId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM crs_course_member
                 WHERE course_id = ?
                   AND role = 'STUDENT'
                   AND join_status = 'ACTIVE'
                   AND is_deleted = FALSE
                """, Long.class, courseId);
    }

    public List<Long> listActiveStudentIds(Long courseId) {
        return jdbcTemplate.queryForList("""
                SELECT user_id FROM crs_course_member
                 WHERE course_id = ?
                   AND role = 'STUDENT'
                   AND join_status = 'ACTIVE'
                   AND is_deleted = FALSE
                 ORDER BY user_id
                """, Long.class, courseId);
    }

    private void bindCourse(PreparedStatement ps, String name, String description, Long teacherId, String semester,
                            String category, String coverUrl, EnrollmentMode enrollmentMode, String inviteCode,
                            Integer maxStudents, LocalDate startDate, LocalDate endDate, CourseStatus status) throws java.sql.SQLException {
        ps.setString(1, name);
        ps.setString(2, description);
        ps.setLong(3, teacherId);
        ps.setString(4, semester);
        ps.setString(5, category);
        ps.setString(6, coverUrl);
        ps.setString(7, enrollmentMode.name());
        ps.setString(8, inviteCode);
        ps.setObject(9, maxStudents);
        ps.setObject(10, startDate);
        ps.setObject(11, endDate);
        ps.setString(12, status.name());
    }

    private EnrollmentMode defaultEnrollmentMode(EnrollmentMode value) {
        return value == null ? EnrollmentMode.PUBLIC : value;
    }

    private CourseStatus defaultStatus(CourseStatus value) {
        return value == null ? CourseStatus.DRAFT : value;
    }

    private String normalizedInviteCode(EnrollmentMode enrollmentMode, String inviteCode) {
        EnrollmentMode mode = defaultEnrollmentMode(enrollmentMode);
        if (mode != EnrollmentMode.INVITE) {
            return inviteCode == null || inviteCode.isBlank() ? null : inviteCode.trim();
        }
        if (inviteCode != null && !inviteCode.isBlank()) {
            return inviteCode.trim();
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private LocalDate nullableDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((java.sql.Date) value).toLocalDate();
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

    private String scopeCondition(String scope, boolean admin) {
        if ("archived".equalsIgnoreCase(scope)) {
            return " AND c.status = 'ARCHIVED'";
        }
        return " AND c.status <> 'ARCHIVED'";
    }

    private boolean requiresUser(String scope, boolean admin) {
        return !admin && ("managed".equalsIgnoreCase(scope) || "archived".equalsIgnoreCase(scope));
    }
}
