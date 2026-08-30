package com.onlinejudge.testsupport;

import com.onlinejudge.common.security.CurrentUserProvider;
import com.onlinejudge.common.security.HeaderCurrentUserProvider;
import com.onlinejudge.auth.service.SessionTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Compatibility fixture for pre-v2 controller tests only. It is compiled into test classes, never
 * the application artifact; production business requests have no header-based authentication bean.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "onlinejudge.test.legacy-header-auth", havingValue = "true", matchIfMissing = true)
public class LegacyHeaderAuthenticationTestAutoConfiguration {
    @Bean
    @Primary
    CurrentUserProvider legacyCurrentUserProvider(
            SessionTokenService sessionTokenService,
            @Value("${onlinejudge.auth.allow-header-auth:true}") boolean allowHeaderAuth
    ) {
        return new LegacySessionOrHeaderCurrentUserProvider(
                sessionTokenService,
                new HeaderCurrentUserProvider(),
                allowHeaderAuth
        );
    }
}
