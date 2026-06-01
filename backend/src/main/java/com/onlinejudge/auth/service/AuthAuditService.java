package com.onlinejudge.auth.service;

import com.onlinejudge.auth.repository.AuthRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthAuditService {
    private final AuthRepository authRepository;

    public AuthAuditService(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long operatorId,
            String operationType,
            String targetType,
            String targetId,
            String resultStatus,
            String failureReason
    ) {
        AuditClient client = currentClient();
        record(operatorId, operationType, targetType, targetId, resultStatus, failureReason, client.clientIp(), client.userAgent());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long operatorId,
            String operationType,
            String targetType,
            String targetId,
            String resultStatus,
            String failureReason,
            String clientIp,
            String userAgent
    ) {
        authRepository.recordAudit(operatorId, operationType, targetType, targetId, resultStatus, failureReason, clientIp, userAgent);
    }

    private AuditClient currentClient() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new AuditClient(null, null);
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return new AuditClient(clientIp, request.getHeader(HttpHeaders.USER_AGENT));
    }

    private record AuditClient(String clientIp, String userAgent) {
    }
}
