package com.onlinejudge.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.exception.ServiceTokenException;
import com.onlinejudge.auth.repository.ServiceTokenIdempotencyRepository;
import com.onlinejudge.auth.security.JwtTokenService;
import com.onlinejudge.auth.service.SessionTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Real MySQL 8.4 regression for the two-Identity-pod service-token replay
 * race.  The disposable shell harness enables this class explicitly; H2 does
 * not substitute for InnoDB's REPEATABLE-READ snapshot semantics.
 */
@EnabledIfEnvironmentVariable(named = "ONLINEJUDGE_LIVE_IDENTITY_SERVICE_TOKENS", matches = "true")
class ServiceTokenMySqlConcurrencyLiveTest {
    private static final String SUBJECT = "CN=course-service";
    private static final String KEY = "service-token-live-concurrency-0001";

    private final String url = System.getProperty("oj.identity.mysql.url");
    private final String username = System.getProperty("oj.identity.mysql.username");
    private final String password = System.getProperty("oj.identity.mysql.password");

    @AfterEach
    void clearIdempotencyRows() {
        new JdbcTemplate(dataSource()).update("DELETE FROM t_identity_service_token_idempotency");
    }

    @Test
    void twoIdentityPodsReturnTheWinnerTokenForTheSameRequestKeyAndPayload() throws Exception {
        CyclicBarrier initialReads = new CyclicBarrier(2);
        ServiceTokenService firstPod = transactionalPod(initialReads);
        ServiceTokenService secondPod = transactionalPod(initialReads);

        List<Outcome> outcomes = race(
                () -> invoke(firstPod, List.of("course:read")),
                () -> invoke(secondPod, List.of("course:read"))
        );

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(outcomes).extracting(Outcome::token).doesNotContainNull().hasSize(2);
        assertThat(outcomes.get(0).token()).isEqualTo(outcomes.get(1).token());
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void twoIdentityPodsRejectAConcurrentChangedPayloadWithConflictInsteadOfServerError() throws Exception {
        CyclicBarrier initialReads = new CyclicBarrier(2);
        ServiceTokenService firstPod = transactionalPod(initialReads);
        ServiceTokenService secondPod = transactionalPod(initialReads);

        List<Outcome> outcomes = race(
                () -> invoke(firstPod, List.of("course:read")),
                () -> invoke(secondPod, List.of("course:write"))
        );

        assertThat(outcomes).filteredOn(outcome -> outcome.failure() == null).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.failure() instanceof ServiceTokenException)
                .singleElement()
                .satisfies(outcome -> assertThat(((ServiceTokenException) outcome.failure()).status().value()).isEqualTo(409));
        assertThat(outcomes).noneMatch(outcome -> outcome.failure() instanceof IllegalStateException);
        assertThat(countRows()).isEqualTo(1);
    }

    private Outcome invoke(ServiceTokenService pod, List<String> scopes) {
        try {
            return Outcome.token(pod.mint(SUBJECT, "course", scopes, KEY).token());
        } catch (RuntimeException failure) {
            return Outcome.failure(failure);
        }
    }

    private ServiceTokenService transactionalPod(CyclicBarrier initialReads) {
        DataSource dataSource = dataSource();
        ServiceTokenIdempotencyRepository repository = new BarrierRepository(new JdbcTemplate(dataSource), initialReads);
        JwtTokenService tokens = new JwtTokenService(
                new ObjectMapper(), mock(SessionTokenService.class),
                JwtTokenService.ISSUER, JwtTokenService.USER_AUDIENCE,
                "identity-live-concurrency", "", "", true
        );
        ServiceTokenService target = new ServiceTokenService(tokens, repository, Duration.ofMinutes(5));
        PlatformTransactionManager manager = new DataSourceTransactionManager(dataSource);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (ServiceTokenService) proxy.getProxy();
    }

    private DataSource dataSource() {
        if (url == null || username == null || password == null) {
            throw new IllegalStateException("live MySQL URL, username and password must be supplied by the harness");
        }
        return new DriverManagerDataSource(url, username, password);
    }

    private List<Outcome> race(Callable<Outcome> left, Callable<Outcome> right) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome> first = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return left.call();
            });
            Future<Outcome> second = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return right.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private int countRows() {
        Integer rows = new JdbcTemplate(dataSource()).queryForObject(
                "SELECT COUNT(*) FROM t_identity_service_token_idempotency", Integer.class);
        return rows == null ? 0 : rows;
    }

    private static final class BarrierRepository extends ServiceTokenIdempotencyRepository {
        private final CyclicBarrier initialReads;
        private boolean initialRead = true;

        private BarrierRepository(JdbcTemplate jdbc, CyclicBarrier initialReads) {
            super(jdbc);
            this.initialReads = initialReads;
        }

        @Override
        public synchronized Optional<StoredRequest> findActive(String workloadSubject, String idempotencyKey, java.time.Instant now) {
            Optional<StoredRequest> result = super.findActive(workloadSubject, idempotencyKey, now);
            if (initialRead) {
                initialRead = false;
                try {
                    initialReads.await(10, TimeUnit.SECONDS);
                } catch (Exception interrupted) {
                    throw new IllegalStateException("two-pod initial read barrier failed", interrupted);
                }
            }
            return result;
        }
    }

    private record Outcome(String token, RuntimeException failure) {
        static Outcome token(String token) {
            return new Outcome(token, null);
        }

        static Outcome failure(RuntimeException failure) {
            return new Outcome(null, failure);
        }
    }
}
