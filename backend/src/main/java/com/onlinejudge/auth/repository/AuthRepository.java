package com.onlinejudge.auth.repository;

import com.onlinejudge.auth.domain.AccountStatus;
import com.onlinejudge.auth.domain.AuthUser;
import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.domain.SessionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AuthUser> userMapper = (rs, rowNum) -> new AuthUser(
            rs.getLong("user_id"),
            rs.getString("username"),
            rs.getString("user_type"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("avatar_url"),
            rs.getString("password_hash"),
            rs.getString("password_salt"),
            AccountStatus.valueOf(rs.getString("account_status")),
            rs.getInt("failed_login_count"),
            toLocalDateTime(rs.getTimestamp("locked_until")),
            toLocalDateTime(rs.getTimestamp("last_login_at"))
    );

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureBaseRolesAndPermissions() {
        upsertRole("STUDENT", "学生", "学生基础角色");
        upsertRole("TEACHER", "教师", "教师基础角色");
        upsertRole("ADMIN", "管理员", "平台管理员角色");
        long studentRoleId = roleId("STUDENT");
        long teacherRoleId = roleId("TEACHER");
        long adminRoleId = roleId("ADMIN");
        assignPermission(studentRoleId, "course:view", "查看课程", "API", "CRS", "/api/v1/courses/**");
        assignPermission(studentRoleId, "homework:submit", "提交作业", "ACTION", "HWK", null);
        assignPermission(studentRoleId, "lab:submit", "提交实验", "ACTION", "LAB", null);
        assignPermission(studentRoleId, "grade:view", "查看成绩", "API", "GRD", null);
        assignPermission(teacherRoleId, "course:manage", "管理课程", "ACTION", "CRS", null);
        assignPermission(teacherRoleId, "homework:manage", "管理作业", "ACTION", "HWK", null);
        assignPermission(teacherRoleId, "lab:manage", "管理实验", "ACTION", "LAB", null);
        assignPermission(teacherRoleId, "grade:manage", "管理成绩", "ACTION", "GRD", null);
        assignPermission(adminRoleId, "auth:manage", "用户权限管理", "ACTION", "AUTH", null);
        assignPermission(adminRoleId, "audit:view", "查看审计日志", "API", "AUTH", "/api/v1/admin/audit-logs");
    }

    public Optional<AuthUser> findUserByUsername(String username) {
        return queryUser("username", username);
    }

    public Optional<AuthUser> findUserByEmail(String email) {
        return queryUser("email", email);
    }

    public Optional<AuthUser> findUserByPhone(String phone) {
        return queryUser("phone", phone);
    }

    public long createUser(
            String username,
            String userType,
            String displayName,
            String phone,
            String email,
            String avatarUrl,
            String passwordHash,
            String passwordSalt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO t_auth_user (
                            username, user_type, display_name, phone, email, avatar_url,
                            password_hash, password_salt, account_status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                username,
                userType,
                displayName,
                phone,
                email,
                avatarUrl,
                passwordHash,
                passwordSalt,
                AccountStatus.ACTIVE.name());
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM t_auth_user WHERE username = ?",
                Long.class,
                username
        );
        return Objects.requireNonNull(userId);
    }

    public void assignRole(long userId, String roleCode, Long assignedBy) {
        long roleId = roleId(roleCode);
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_user_role WHERE user_id = ? AND role_id = ?",
                Integer.class,
                userId,
                roleId
        );
        if (existing != null && existing > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO t_auth_user_role (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                userId,
                roleId,
                assignedBy
        );
    }

    public void updateFailedLogin(long userId, int failedLoginCount) {
        jdbcTemplate.update("UPDATE t_auth_user SET failed_login_count = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                failedLoginCount,
                userId);
    }

    public void updateAccountStatus(long userId, AccountStatus status) {
        jdbcTemplate.update("UPDATE t_auth_user SET account_status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                status.name(),
                userId);
    }

    public void resetLoginFailure(long userId, LocalDateTime lastLoginAt) {
        jdbcTemplate.update("""
                        UPDATE t_auth_user
                        SET failed_login_count = 0, last_login_at = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                        """,
                Timestamp.valueOf(lastLoginAt),
                userId);
    }

    public void createSession(long userId, String token, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO t_auth_session (user_id, token_id, issued_at, expires_at, status)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                userId,
                token,
                Timestamp.valueOf(issuedAt),
                Timestamp.valueOf(expiresAt),
                SessionStatus.VALID.name());
    }

    public Optional<AuthUserView> findValidSessionUser(String token, LocalDateTime now) {
        List<Long> userIds = jdbcTemplate.query("""
                        SELECT user_id FROM t_auth_session
                        WHERE token_id = ? AND status = ? AND expires_at > ?
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                token,
                SessionStatus.VALID.name(),
                Timestamp.valueOf(now));
        if (userIds.isEmpty()) {
            return Optional.empty();
        }
        AuthUserView user = toUserView(userIds.get(0));
        if (!"ACTIVE".equals(user.accountStatus())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public void revokeSession(String token, LocalDateTime revokedAt) {
        jdbcTemplate.update("""
                        UPDATE t_auth_session
                        SET status = ?, revoked_at = ?
                        WHERE token_id = ? AND status = ?
                        """,
                SessionStatus.REVOKED.name(),
                Timestamp.valueOf(revokedAt),
                token,
                SessionStatus.VALID.name());
    }

    public AuthUserView toUserView(long userId) {
        AuthUser user = jdbcTemplate.queryForObject(
                "SELECT * FROM t_auth_user WHERE user_id = ? AND deleted = FALSE",
                userMapper,
                userId
        );
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        return new AuthUserView(
                user.id(),
                user.username(),
                user.userType(),
                user.displayName(),
                user.phone(),
                user.email(),
                user.avatarUrl(),
                user.accountStatus().name(),
                roles(user.id()),
                permissions(user.id())
        );
    }

    private Optional<AuthUser> queryUser(String field, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        List<AuthUser> users = jdbcTemplate.query(
                "SELECT * FROM t_auth_user WHERE " + field + " = ? AND deleted = FALSE",
                userMapper,
                value.trim()
        );
        return users.stream().findFirst();
    }

    private List<String> roles(long userId) {
        return jdbcTemplate.query("""
                        SELECT r.role_code FROM t_auth_user_role ur
                        JOIN t_auth_role r ON r.role_id = ur.role_id
                        WHERE ur.user_id = ? AND r.enabled = TRUE AND r.deleted = FALSE
                        ORDER BY r.role_code
                        """,
                (rs, rowNum) -> rs.getString("role_code"),
                userId);
    }

    private List<String> permissions(long userId) {
        return jdbcTemplate.query("""
                        SELECT DISTINCT p.permission_code FROM t_auth_user_role ur
                        JOIN t_auth_role r ON r.role_id = ur.role_id
                        JOIN t_auth_role_permission rp ON rp.role_id = r.role_id
                        JOIN t_auth_permission p ON p.permission_id = rp.permission_id
                        WHERE ur.user_id = ? AND r.enabled = TRUE AND r.deleted = FALSE
                          AND p.enabled = TRUE AND p.deleted = FALSE
                        ORDER BY p.permission_code
                        """,
                (rs, rowNum) -> rs.getString("permission_code"),
                userId);
    }

    private void upsertRole(String code, String name, String description) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_role WHERE role_code = ?",
                Integer.class,
                code
        );
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO t_auth_role (role_code, role_name, description) VALUES (?, ?, ?)",
                    code,
                    name,
                    description
            );
        }
    }

    private void assignPermission(long roleId, String code, String name, String type, String moduleCode, String pattern) {
        long permissionId = permissionId(code, name, type, moduleCode, pattern);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_role_permission WHERE role_id = ? AND permission_id = ?",
                Integer.class,
                roleId,
                permissionId
        );
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO t_auth_role_permission (role_id, permission_id) VALUES (?, ?)",
                    roleId,
                    permissionId
            );
        }
    }

    private long roleId(String roleCode) {
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT role_id FROM t_auth_role WHERE role_code = ? AND deleted = FALSE",
                Long.class,
                roleCode
        );
        return Objects.requireNonNull(roleId);
    }

    private long permissionId(String code, String name, String type, String moduleCode, String pattern) {
        List<Long> existing = jdbcTemplate.query(
                "SELECT permission_id FROM t_auth_permission WHERE permission_code = ?",
                (rs, rowNum) -> rs.getLong("permission_id"),
                code
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbcTemplate.update("""
                        INSERT INTO t_auth_permission (
                            permission_code, permission_name, permission_type, module_code, resource_pattern
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                code,
                name,
                type,
                moduleCode,
                pattern);
        Long permissionId = jdbcTemplate.queryForObject(
                "SELECT permission_id FROM t_auth_permission WHERE permission_code = ?",
                Long.class,
                code
        );
        return Objects.requireNonNull(permissionId);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
