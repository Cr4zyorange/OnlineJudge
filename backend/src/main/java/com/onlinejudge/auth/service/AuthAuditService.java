package com.onlinejudge.auth.service;

import com.onlinejudge.auth.repository.AuthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        authRepository.recordAudit(operatorId, operationType, targetType, targetId, resultStatus, failureReason);
    }
}
