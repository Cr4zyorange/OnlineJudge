package com.onlinejudge.lrn.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PersistentNotificationEventPublisher implements NotificationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PersistentNotificationEventPublisher.class);

    private final NotificationService notificationService;
    private final TransactionTemplate notificationTransaction;

    public PersistentNotificationEventPublisher(
            NotificationService notificationService,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationService = notificationService;
        this.notificationTransaction = new TransactionTemplate(transactionManager);
        this.notificationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void publish(NotificationEvent event) {
        if (event == null) {
            return;
        }
        if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    persist(event);
                }
            });
            return;
        }
        persist(event);
    }

    private void persist(NotificationEvent event) {
        try {
            notificationTransaction.executeWithoutResult(ignored ->
                    notificationService.createNotifications(new NotificationCreateCommand(
                            event.idempotencyKey(),
                            event.type(),
                            null,
                            event.courseId() > 0 ? event.courseId() : null,
                            normalizeSourceModule(event),
                            event.targetId(),
                            event.recipientUserIds(),
                            event.title(),
                            event.content(),
                            1,
                            event.linkUrl()
                    )));
        } catch (RuntimeException ex) {
            log.warn("Failed to persist notification event targetType={} targetId={}",
                    event.targetType(), event.targetId(), ex);
        }
    }

    private String normalizeSourceModule(NotificationEvent event) {
        String eventType = event.type() == null ? "" : event.type().trim().toUpperCase();
        if (eventType.startsWith("GRADE") || eventType.startsWith("GRD")) {
            return "GRD";
        }
        String targetType = event.targetType();
        if (targetType == null || targetType.isBlank()) {
            return "SYS";
        }
        String normalized = targetType.trim().toUpperCase();
        if (normalized.startsWith("HOMEWORK") || normalized.startsWith("HWK")) {
            return "HWK";
        }
        if (normalized.startsWith("LAB") || normalized.startsWith("EXPERIMENT")) {
            return "LAB";
        }
        if (normalized.startsWith("GRADE") || normalized.startsWith("GRD")) {
            return "GRD";
        }
        if (normalized.startsWith("COURSE") || normalized.startsWith("CRS") || normalized.startsWith("ANNOUNCEMENT")) {
            return "CRS";
        }
        return "SYS";
    }
}
