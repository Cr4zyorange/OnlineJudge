package com.onlinejudge.auth.repository;

import com.onlinejudge.auth.security.JwtTokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Shared, expiring service-token replay records; never a process-local cache. */
@Repository
public class ServiceTokenIdempotencyRepository {
    private final JdbcTemplate jdbc;

    public ServiceTokenIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredRequest> findActive(String workloadSubject, String idempotencyKey, Instant now) {
        return find(workloadSubject, idempotencyKey, now, "");
    }

    /**
     * InnoDB current read used only after a duplicate unique-key insert.  It
     * is deliberately a separate, autocommit statement after the service has
     * suspended any caller transaction, so it can observe the winning pod.
     */
    public Optional<StoredRequest> findActiveCurrent(String workloadSubject, String idempotencyKey, Instant now) {
        return find(workloadSubject, idempotencyKey, now, " FOR UPDATE");
    }

    private Optional<StoredRequest> find(String workloadSubject, String idempotencyKey, Instant now, String lockClause) {
        List<StoredRequest> rows = jdbc.query("""
                        SELECT request_fingerprint, access_token, expires_at
                          FROM t_identity_service_token_idempotency
                         WHERE workload_subject = ? AND idempotency_key = ? AND expires_at > ?
                        """ + lockClause,
                (resultSet, rowNumber) -> new StoredRequest(
                        resultSet.getString("request_fingerprint"),
                        new JwtTokenService.IssuedServiceToken(
                                resultSet.getString("access_token"),
                                resultSet.getTimestamp("expires_at").toInstant()
                        )
                ), workloadSubject, idempotencyKey, Timestamp.from(now));
        return rows.stream().findFirst();
    }

    /** Returns false when another Identity replica won the same key concurrently. */
    public boolean insert(
            String workloadSubject,
            String idempotencyKey,
            String fingerprint,
            JwtTokenService.IssuedServiceToken token,
            Instant now
    ) {
        try {
            return jdbc.update("""
                            INSERT INTO t_identity_service_token_idempotency (
                                workload_subject, idempotency_key, request_fingerprint,
                                access_token, expires_at, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    workloadSubject, idempotencyKey, fingerprint, token.token(),
                    Timestamp.from(token.expiresAt()), Timestamp.from(now)) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    /** Bounded token TTL makes this both replay-safe and self-cleaning. */
    public void deleteExpired(Instant now) {
        jdbc.update("DELETE FROM t_identity_service_token_idempotency WHERE expires_at <= ?", Timestamp.from(now));
    }

    public record StoredRequest(String fingerprint, JwtTokenService.IssuedServiceToken token) {
    }
}
