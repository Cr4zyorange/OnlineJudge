package com.onlinejudge.auth.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the background-only JWKS refresh loop without adding Identity to request authentication. */
@Configuration
@EnableScheduling
public class IdentityJwksRefreshConfiguration {
}
