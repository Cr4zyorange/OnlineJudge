package com.onlinejudge.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.exception.ServiceTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * Reads only the client certificate installed by the TLS terminator/container. No header, password,
 * or shared internal secret is accepted as a workload identity.
 */
@Service
public class WorkloadIdentityAuthenticator {
    private final Map<String, WorkloadPolicy> policies;

    public WorkloadIdentityAuthenticator(
            ObjectMapper objectMapper,
            @Value("${onlinejudge.identity.service-tokens.workloads:{}}") String configuredPolicies
    ) {
        try {
            Map<String, Map<String, Object>> raw = objectMapper.readValue(configuredPolicies, new TypeReference<>() { });
            this.policies = raw.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> WorkloadPolicy.from(entry.getValue())
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("IDENTITY_SERVICE_TOKEN_WORKLOADS must be a JSON mTLS subject policy", exception);
        }
    }

    public String authorize(HttpServletRequest request, String audience, List<String> scopes) {
        X509Certificate certificate = clientCertificate(request);
        if (certificate == null) {
            throw ServiceTokenException.identityInvalid();
        }
        String subject = certificate.getSubjectX500Principal().getName();
        WorkloadPolicy policy = policies.get(subject);
        if (policy == null || !policy.audiences().contains(audience) || !policy.scopes().containsAll(scopes)) {
            throw ServiceTokenException.forbidden();
        }
        return subject;
    }

    private X509Certificate clientCertificate(HttpServletRequest request) {
        Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!(value instanceof X509Certificate[] certificates) || certificates.length == 0 || certificates[0] == null) {
            value = request.getAttribute("javax.servlet.request.X509Certificate");
        }
        if (value instanceof X509Certificate[] certificates && certificates.length > 0) {
            return certificates[0];
        }
        return null;
    }

    private record WorkloadPolicy(List<String> audiences, List<String> scopes) {
        private static WorkloadPolicy from(Map<String, Object> raw) {
            return new WorkloadPolicy(strings(raw.get("audiences")), strings(raw.get("scopes")));
        }

        private static List<String> strings(Object value) {
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
    }
}
