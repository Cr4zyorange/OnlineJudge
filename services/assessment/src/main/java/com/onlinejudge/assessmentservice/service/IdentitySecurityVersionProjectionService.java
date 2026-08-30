package com.onlinejudge.assessmentservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local revocation floor for Identity's ordered security-version facts; a gap never weakens a newer floor. */
@Service
public class IdentitySecurityVersionProjectionService {
    private final JdbcTemplate jdbc;
    public IdentitySecurityVersionProjectionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public ApplyResult apply(SecurityVersionChanged event) {
        Integer seen = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_identity_security_version_event_inbox WHERE event_id=?", Integer.class, event.eventId());
        if (seen != null && seen > 0) return new ApplyResult("DUPLICATE");
        advanceMinimum(event.userId(), event.securityVersion());
        long current = currentVersion(event.userId());
        if (event.aggregateVersion() > current + 1) {
            defer(event, current + 1);
            return new ApplyResult("GAP");
        }
        if (event.aggregateVersion() <= current) {
            record(event);
            return new ApplyResult("STALE");
        }
        applyInOrder(event);
        drainDeferred(event.userId(), event.aggregateVersion());
        return new ApplyResult("APPLIED");
    }

    public long minimumFor(String userId) {
        return jdbc.query("SELECT minimum_security_version FROM assessment_identity_security_version WHERE user_id=?", (rs, ignored) -> rs.getLong(1), userId)
                .stream().findFirst().orElse(1L);
    }

    private long currentVersion(String userId) {
        return jdbc.query("SELECT aggregate_version FROM assessment_identity_security_version WHERE user_id=?", (rs, ignored) -> rs.getLong(1), userId)
                .stream().findFirst().orElse(1L);
    }

    private void advanceMinimum(String userId, long securityVersion) {
        jdbc.update("""
                INSERT INTO assessment_identity_security_version (user_id, minimum_security_version, aggregate_version)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE minimum_security_version=GREATEST(minimum_security_version, VALUES(minimum_security_version))
                """, userId, securityVersion);
    }

    private void defer(SecurityVersionChanged event, long expectedVersion) {
        jdbc.update("""
                INSERT INTO assessment_deferred_identity_security_version_event
                    (event_id, user_id, security_version, change_reason, aggregate_version)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE event_id=VALUES(event_id), security_version=VALUES(security_version), change_reason=VALUES(change_reason)
                """, event.eventId(), event.userId(), event.securityVersion(), event.changeReason(), event.aggregateVersion());
        jdbc.update("""
                INSERT INTO assessment_identity_security_version_gap (user_id, expected_version, observed_version)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE expected_version=VALUES(expected_version), observed_version=GREATEST(observed_version, VALUES(observed_version))
                """, event.userId(), expectedVersion, event.aggregateVersion());
    }

    private void applyInOrder(SecurityVersionChanged event) {
        jdbc.update("UPDATE assessment_identity_security_version SET aggregate_version=?, minimum_security_version=GREATEST(minimum_security_version, ?) WHERE user_id=?", event.aggregateVersion(), event.securityVersion(), event.userId());
        jdbc.update("DELETE FROM assessment_identity_security_version_gap WHERE user_id=? AND expected_version <= ?", event.userId(), event.aggregateVersion());
        record(event);
    }

    private void drainDeferred(String userId, long current) {
        long next = current + 1;
        while (true) {
            var deferred = jdbc.query("SELECT event_id, security_version, change_reason, aggregate_version FROM assessment_deferred_identity_security_version_event WHERE user_id=? AND aggregate_version=?", (rs, ignored) -> new SecurityVersionChanged(rs.getString(1), userId, rs.getLong(2), rs.getString(3), rs.getLong(4)), userId, next).stream().findFirst();
            if (deferred.isEmpty()) return;
            SecurityVersionChanged event = deferred.orElseThrow();
            applyInOrder(event);
            jdbc.update("DELETE FROM assessment_deferred_identity_security_version_event WHERE event_id=?", event.eventId());
            next++;
        }
    }

    private void record(SecurityVersionChanged event) {
        jdbc.update("INSERT INTO assessment_identity_security_version_event_inbox (event_id, user_id, aggregate_version) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)", event.eventId(), event.userId(), event.aggregateVersion());
    }

    public record SecurityVersionChanged(String eventId, String userId, long securityVersion, String changeReason, long aggregateVersion) {
        public SecurityVersionChanged(String eventId, String userId, long securityVersion, String changeReason) { this(eventId, userId, securityVersion, changeReason, securityVersion); }
    }
    public record ApplyResult(String decision) { }
}
