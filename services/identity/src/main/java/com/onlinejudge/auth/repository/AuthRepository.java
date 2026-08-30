package com.onlinejudge.auth.repository;

import com.onlinejudge.auth.domain.AccountStatus;
import com.onlinejudge.auth.domain.AuthAuditLogView;
import com.onlinejudge.auth.domain.AuthUser;
import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.domain.PermissionView;
import com.onlinejudge.auth.domain.RoleView;
import com.onlinejudge.auth.domain.SessionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
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
            rs.getLong("security_version"),
            rs.getInt("failed_login_count"),
            toLocalDateTime(rs.getTimestamp("locked_until")),
            toLocalDateTime(rs.getTimestamp("last_login_at"))
    );

    private final RowMapper<PermissionView> permissionMapper = (rs, rowNum) -> new PermissionView(
            rs.getLong("permission_id"),
            rs.getString("permission_code"),
            rs.getString("permission_name"),
            rs.getString("permission_type"),
            rs.getString("module_code"),
            rs.getString("resource_pattern"),
            rs.getBoolean("enabled")
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

    public Optional<AuthUser> findUserByLoginIdentifier(String account) {
        return findUserByUsername(account)
                .or(() -> findUserByEmail(account))
                .or(() -> findUserByPhone(account));
    }

    public Optional<AuthUser> findUserById(long userId) {
        List<AuthUser> users = jdbcTemplate.query(
                "SELECT * FROM t_auth_user WHERE user_id = ? AND deleted = FALSE",
                userMapper,
                userId
        );
        return users.stream().findFirst();
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

    /**
     * Increments the counter inside one row-level UPDATE. Login attempts are intentionally never
     * read-modify-written in Java: concurrent bad passwords must not collapse into one attempt.
     */
    public FailedLoginState recordFailedLoginAttempt(
            long userId,
            LocalDateTime now,
            int lockThreshold,
            LocalDateTime lockUntil
    ) {
        jdbcTemplate.update("""
                        UPDATE t_auth_user
                        SET failed_login_count = CASE
                                WHEN locked_until IS NOT NULL AND locked_until <= ? THEN 1
                                ELSE failed_login_count + 1
                            END,
                            locked_until = CASE
                                WHEN (CASE
                                    WHEN locked_until IS NOT NULL AND locked_until <= ? THEN 1
                                    ELSE failed_login_count + 1
                                END) >= ? THEN ?
                                ELSE NULL
                            END,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND deleted = FALSE
                        """,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                lockThreshold,
                Timestamp.valueOf(lockUntil),
                userId);
        return jdbcTemplate.queryForObject("""
                        SELECT failed_login_count, locked_until
                        FROM t_auth_user
                        WHERE user_id = ? AND deleted = FALSE
                        """,
                (rs, rowNum) -> new FailedLoginState(
                        rs.getInt("failed_login_count"),
                        toLocalDateTime(rs.getTimestamp("locked_until"))),
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
                        SET failed_login_count = 0, locked_until = NULL, last_login_at = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                        """,
                Timestamp.valueOf(lastLoginAt),
                userId);
    }

    public void updateProfile(long userId, String displayName, String phone, String email, String avatarUrl) {
        jdbcTemplate.update("""
                        UPDATE t_auth_user
                        SET display_name = ?, phone = ?, email = ?, avatar_url = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND deleted = FALSE
                        """,
                displayName,
                phone,
                email,
                avatarUrl,
                userId);
    }

    public void updatePassword(long userId, String passwordHash, String passwordSalt) {
        jdbcTemplate.update("""
                        UPDATE t_auth_user
                        SET password_hash = ?, password_salt = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND deleted = FALSE
                        """,
                passwordHash,
                passwordSalt,
                userId);
    }

    public void revokeUserSessions(long userId, LocalDateTime revokedAt) {
        jdbcTemplate.update("""
                        UPDATE t_auth_session
                        SET status = ?, revoked_at = ?
                        WHERE user_id = ? AND status = ?
                        """,
                SessionStatus.REVOKED.name(),
                Timestamp.valueOf(revokedAt),
                userId,
                SessionStatus.VALID.name());
    }

    public long createSession(long userId, String tokenId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO t_auth_session (user_id, token_id, issued_at, expires_at, status)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                userId,
                tokenId,
                Timestamp.valueOf(issuedAt),
                Timestamp.valueOf(expiresAt),
                SessionStatus.VALID.name());
        Long sessionId = jdbcTemplate.queryForObject(
                "SELECT session_id FROM t_auth_session WHERE token_id = ?",
                Long.class,
                tokenId);
        return Objects.requireNonNull(sessionId);
    }

    public Optional<AuthUserView> findValidJwtSessionUser(
            long sessionId,
            String tokenId,
            long userId,
            long securityVersion,
            LocalDateTime now
    ) {
        List<Long> userIds = jdbcTemplate.query("""
                        SELECT s.user_id
                        FROM t_auth_session s
                        JOIN t_auth_user u ON u.user_id = s.user_id
                        WHERE s.session_id = ?
                          AND s.token_id = ?
                          AND s.user_id = ?
                          AND s.status = ?
                          AND s.expires_at > ?
                          AND u.account_status = ?
                          AND u.security_version = ?
                          AND u.deleted = FALSE
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                sessionId,
                tokenId,
                userId,
                SessionStatus.VALID.name(),
                Timestamp.valueOf(now),
                AccountStatus.ACTIVE.name(),
                securityVersion);
        return userIds.isEmpty() ? Optional.empty() : Optional.of(toUserView(userIds.get(0)));
    }

    public Optional<AuthUserView> findValidSessionUser(String tokenId, LocalDateTime now) {
        List<Long> userIds = jdbcTemplate.query("""
                        SELECT user_id FROM t_auth_session
                        WHERE token_id = ? AND status = ? AND expires_at > ?
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                tokenId,
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

    public Optional<AuthUserView> findSessionUser(String tokenId) {
        List<Long> userIds = jdbcTemplate.query("""
                        SELECT user_id FROM t_auth_session
                        WHERE token_id = ?
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                tokenId);
        if (userIds.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toUserView(userIds.get(0)));
    }

    public void revokeSession(String tokenId, LocalDateTime revokedAt) {
        jdbcTemplate.update("""
                        UPDATE t_auth_session
                        SET status = ?, revoked_at = ?
                        WHERE token_id = ? AND status = ?
                        """,
                SessionStatus.REVOKED.name(),
                Timestamp.valueOf(revokedAt),
                tokenId,
                SessionStatus.VALID.name());
    }

    public long incrementSecurityVersion(long userId) {
        jdbcTemplate.update("""
                        UPDATE t_auth_user
                        SET security_version = security_version + 1, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND deleted = FALSE
                        """, userId);
        Long version = jdbcTemplate.queryForObject(
                "SELECT security_version FROM t_auth_user WHERE user_id = ? AND deleted = FALSE",
                Long.class,
                userId);
        return Objects.requireNonNull(version);
    }

    public List<Long> userIdsForRole(long roleId) {
        return jdbcTemplate.query(
                "SELECT user_id FROM t_auth_user_role WHERE role_id = ? ORDER BY user_id",
                (rs, rowNum) -> rs.getLong("user_id"),
                roleId);
    }

    public void recordSecurityVersionOutbox(
            String eventId,
            long userId,
            long securityVersion,
            LocalDateTime occurredAt,
            String correlationId,
            String payloadJson
    ) {
        jdbcTemplate.update("""
                        INSERT INTO t_identity_outbox_event (
                            event_id, event_type, payload_version, aggregate_type, aggregate_id,
                            aggregate_version, occurred_at, correlation_id, payload_json, delivery_status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                eventId,
                "identity.security-version.changed.v2",
                2,
                "identity-user",
                String.valueOf(userId),
                securityVersion,
                Timestamp.valueOf(occurredAt),
                correlationId,
                payloadJson,
                "PENDING");
    }

    public void recordAudit(
            Long operatorId,
            String operationType,
            String targetType,
            String targetId,
            String resultStatus,
            String failureReason
    ) {
        recordAudit(operatorId, operationType, targetType, targetId, resultStatus, failureReason, null, null);
    }

    public void recordAudit(
            Long operatorId,
            String operationType,
            String targetType,
            String targetId,
            String resultStatus,
            String failureReason,
            String clientIp,
            String userAgent
    ) {
        jdbcTemplate.update("""
                        INSERT INTO t_auth_audit_log (
                            operator_id, operation_type, target_type, target_id, result_status, failure_reason,
                            client_ip, user_agent
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                operatorId,
                operationType,
                targetType,
                targetId,
                resultStatus,
                failureReason,
                clientIp,
                userAgent);
    }

    public List<AuthAuditLogView> listAuditLogs(
            Long operatorId,
            String operationType,
            String resultStatus,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int page,
            int size
    ) {
        QueryParts query = auditLogQuery(operatorId, operationType, resultStatus, startTime, endTime);
        List<Object> args = new ArrayList<>(query.args());
        args.add(size);
        args.add((page - 1) * size);
        return jdbcTemplate.query("""
                        SELECT log_id, operator_id, operation_type, target_type, target_id, result_status,
                               failure_reason, client_ip, user_agent, created_at
                        FROM t_auth_audit_log
                        """ + query.where() + """
                        ORDER BY created_at DESC, log_id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new AuthAuditLogView(
                        rs.getLong("log_id"),
                        rs.getObject("operator_id") == null ? null : rs.getLong("operator_id"),
                        rs.getString("operation_type"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("result_status"),
                        rs.getString("failure_reason"),
                        rs.getString("client_ip"),
                        rs.getString("user_agent"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                args.toArray());
    }

    public long countAuditLogs(
            Long operatorId,
            String operationType,
            String resultStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        QueryParts query = auditLogQuery(operatorId, operationType, resultStatus, startTime, endTime);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_audit_log " + query.where(),
                Long.class,
                query.args().toArray()
        );
        return count == null ? 0 : count;
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

    public List<AuthUserView> listUsers(String keyword, String role, String status, int page, int size) {
        UserFilter filter = normalizeUserFilter(keyword, role, status);
        List<Long> userIds = jdbcTemplate.query("""
                        SELECT DISTINCT u.user_id
                        FROM t_auth_user u
                        LEFT JOIN t_auth_user_role ur ON ur.user_id = u.user_id
                        LEFT JOIN t_auth_role r ON r.role_id = ur.role_id
                        WHERE u.deleted = FALSE
                          AND (? IS NULL OR u.username LIKE ? OR u.display_name LIKE ? OR u.email LIKE ?)
                          AND (? IS NULL OR r.role_code = ?)
                          AND (? IS NULL OR u.account_status = ?)
                        ORDER BY u.user_id
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                filter.keywordLike(),
                filter.keywordLike(),
                filter.keywordLike(),
                filter.keywordLike(),
                filter.role(),
                filter.role(),
                filter.status(),
                filter.status(),
                size,
                (page - 1) * size);
        return userIds.stream().map(this::toUserView).toList();
    }

    public long countUsers(String keyword, String role, String status) {
        UserFilter filter = normalizeUserFilter(keyword, role, status);
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(DISTINCT u.user_id)
                        FROM t_auth_user u
                        LEFT JOIN t_auth_user_role ur ON ur.user_id = u.user_id
                        LEFT JOIN t_auth_role r ON r.role_id = ur.role_id
                        WHERE u.deleted = FALSE
                          AND (? IS NULL OR u.username LIKE ? OR u.display_name LIKE ? OR u.email LIKE ?)
                          AND (? IS NULL OR r.role_code = ?)
                          AND (? IS NULL OR u.account_status = ?)
                        """,
                Long.class,
                filter.keywordLike(),
                filter.keywordLike(),
                filter.keywordLike(),
                filter.keywordLike(),
                filter.role(),
                filter.role(),
                filter.status(),
                filter.status());
        return count == null ? 0 : count;
    }

    public boolean userExists(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_user WHERE user_id = ? AND deleted = FALSE",
                Integer.class,
                userId
        );
        return count != null && count > 0;
    }

    public List<RoleView> listRoles() {
        List<Long> roleIds = jdbcTemplate.query("""
                        SELECT role_id FROM t_auth_role
                        WHERE deleted = FALSE
                        ORDER BY role_code
                        """,
                (rs, rowNum) -> rs.getLong("role_id"));
        return roleIds.stream().map(this::roleView).toList();
    }

    public RoleView roleView(long roleId) {
        return jdbcTemplate.queryForObject("""
                        SELECT role_id, role_code, role_name, description, enabled
                        FROM t_auth_role
                        WHERE role_id = ? AND deleted = FALSE
                        """,
                (rs, rowNum) -> new RoleView(
                        rs.getLong("role_id"),
                        rs.getString("role_code"),
                        rs.getString("role_name"),
                        rs.getString("description"),
                        rs.getBoolean("enabled"),
                        permissionsByRoleId(rs.getLong("role_id"))
                ),
                roleId);
    }

    public List<PermissionView> listPermissions() {
        return jdbcTemplate.query("""
                        SELECT permission_id, permission_code, permission_name, permission_type,
                               module_code, resource_pattern, enabled
                        FROM t_auth_permission
                        WHERE deleted = FALSE
                        ORDER BY module_code, permission_code
                        """,
                permissionMapper);
    }

    public long createRole(String roleCode, String roleName, String description, boolean enabled) {
        jdbcTemplate.update("""
                        INSERT INTO t_auth_role (role_code, role_name, description, enabled)
                        VALUES (?, ?, ?, ?)
                        """,
                roleCode,
                roleName,
                description,
                enabled);
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT role_id FROM t_auth_role WHERE role_code = ?",
                Long.class,
                roleCode);
        return Objects.requireNonNull(roleId);
    }

    public void updateRole(long roleId, String roleCode, String roleName, String description, boolean enabled) {
        jdbcTemplate.update("""
                        UPDATE t_auth_role
                        SET role_code = ?, role_name = ?, description = ?, enabled = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE role_id = ? AND deleted = FALSE
                        """,
                roleCode,
                roleName,
                description,
                enabled,
                roleId);
    }

    public boolean enabledRolesExist(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        return roleIds.stream().distinct().allMatch(this::enabledRoleExists);
    }

    public boolean enabledPermissionsExist(List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return true;
        }
        return permissionIds.stream().distinct().allMatch(this::enabledPermissionExists);
    }

    public void replaceUserRoles(long userId, List<Long> roleIds, long assignedBy) {
        jdbcTemplate.update("DELETE FROM t_auth_user_role WHERE user_id = ?", userId);
        for (Long roleId : roleIds.stream().distinct().toList()) {
            jdbcTemplate.update(
                    "INSERT INTO t_auth_user_role (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                    userId,
                    roleId,
                    assignedBy
            );
        }
    }

    public void replaceRolePermissions(long roleId, List<Long> permissionIds, long assignedBy) {
        jdbcTemplate.update("DELETE FROM t_auth_role_permission WHERE role_id = ?", roleId);
        for (Long permissionId : permissionIds.stream().distinct().toList()) {
            jdbcTemplate.update(
                    "INSERT INTO t_auth_role_permission (role_id, permission_id, assigned_by) VALUES (?, ?, ?)",
                    roleId,
                    permissionId,
                    assignedBy
            );
        }
    }

    public boolean roleExists(long roleId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_role WHERE role_id = ? AND deleted = FALSE",
                Integer.class,
                roleId
        );
        return count != null && count > 0;
    }

    private boolean enabledRoleExists(long roleId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_role WHERE role_id = ? AND enabled = TRUE AND deleted = FALSE",
                Integer.class,
                roleId
        );
        return count != null && count > 0;
    }

    private boolean enabledPermissionExists(long permissionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_permission WHERE permission_id = ? AND enabled = TRUE AND deleted = FALSE",
                Integer.class,
                permissionId
        );
        return count != null && count > 0;
    }

    private Optional<AuthUser> queryUser(String field, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String sql = switch (field) {
            case "username" -> "SELECT * FROM t_auth_user WHERE username = ? AND deleted = FALSE";
            case "email" -> "SELECT * FROM t_auth_user WHERE email = ? AND deleted = FALSE";
            case "phone" -> "SELECT * FROM t_auth_user WHERE phone = ? AND deleted = FALSE";
            default -> throw new IllegalArgumentException("unsupported user lookup field");
        };
        List<AuthUser> users = jdbcTemplate.query(
                sql,
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

    private List<PermissionView> permissionsByRoleId(long roleId) {
        return jdbcTemplate.query("""
                        SELECT p.permission_id, p.permission_code, p.permission_name, p.permission_type,
                               p.module_code, p.resource_pattern, p.enabled
                        FROM t_auth_role_permission rp
                        JOIN t_auth_permission p ON p.permission_id = rp.permission_id
                        WHERE rp.role_id = ? AND p.deleted = FALSE
                        ORDER BY p.module_code, p.permission_code
                        """,
                permissionMapper,
                roleId);
    }

    private UserFilter normalizeUserFilter(String keyword, String role, String status) {
        String keywordLike = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        String normalizedRole = role == null || role.isBlank() ? null : role.trim();
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
        return new UserFilter(keywordLike, normalizedRole, normalizedStatus);
    }

    private QueryParts auditLogQuery(
            Long operatorId,
            String operationType,
            String resultStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (operatorId != null) {
            conditions.add("operator_id = ?");
            args.add(operatorId);
        }
        if (operationType != null && !operationType.isBlank()) {
            conditions.add("operation_type = ?");
            args.add(operationType.trim());
        }
        if (resultStatus != null && !resultStatus.isBlank()) {
            conditions.add("result_status = ?");
            args.add(resultStatus.trim());
        }
        if (startTime != null) {
            conditions.add("created_at >= ?");
            args.add(Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            conditions.add("created_at <= ?");
            args.add(Timestamp.valueOf(endTime));
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions) + " ";
        return new QueryParts(where, args);
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

    private record UserFilter(String keywordLike, String role, String status) {
    }

    private record QueryParts(String where, List<Object> args) {
    }

    public record FailedLoginState(int failedLoginCount, LocalDateTime lockedUntil) {
    }
}
