package com.onlinejudge.lrn.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;

@Component
public class PersistentNotificationEventPublisher implements NotificationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PersistentNotificationEventPublisher.class);

    private final NotificationService notificationService;
    private final TransactionTemplate notificationTransaction;
    private final Executor notificationDeliveryExecutor;

    public PersistentNotificationEventPublisher(
            NotificationService notificationService,
            PlatformTransactionManager transactionManager,
            @Qualifier("notificationDeliveryExecutor") Executor notificationDeliveryExecutor
    ) {
        this.notificationService = notificationService;
        this.notificationTransaction = new TransactionTemplate(transactionManager);
        this.notificationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.notificationDeliveryExecutor = notificationDeliveryExecutor;
    }

    @Override
    public void publish(NotificationEvent event) {
        if (shouldSkip(event)) {
            return;
        }
        NotificationCreateCommand command = toCommand(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                log.warn("Skipped best-effort notification because transaction synchronization is unavailable "
                                + "targetType={} targetId={}",
                        event.targetType(), event.targetId());
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(command, event);
                }
            });
            return;
        }
        persistBestEffort(command, event);
    }

    @Override
    public void publishRequired(NotificationEvent event) {
        if (shouldSkip(event)) {
            return;
        }
        notificationService.createNotifications(toCommand(event));
    }

    private void dispatch(NotificationCreateCommand command, NotificationEvent event) {
        try {
            notificationDeliveryExecutor.execute(() -> persistBestEffort(command, event));
        } catch (RuntimeException ex) {
            log.warn("Failed to schedule best-effort notification targetType={} targetId={}",
                    event.targetType(), event.targetId(), ex);
        }
    }

    private void persistBestEffort(NotificationCreateCommand command, NotificationEvent event) {
        try {
            notificationTransaction.executeWithoutResult(ignored ->
                    notificationService.createNotifications(command));
        } catch (RuntimeException ex) {
            log.warn("Failed to persist best-effort notification targetType={} targetId={}",
                    event.targetType(), event.targetId(), ex);
        }
    }

    private boolean shouldSkip(NotificationEvent event) {
        return event == null
                || event.recipientUserIds() == null
                || event.recipientUserIds().isEmpty();
    }

    private NotificationCreateCommand toCommand(NotificationEvent event) {
        return new NotificationCreateCommand(
                event.idempotencyKey(),
                event.type(),
                null,
                event.courseId() > 0 ? event.courseId() : null,
                normalizeSourceModule(event),
                event.targetId(),
                event.recipientUserIds().stream().toList(),
                event.title(),
                event.content(),
                1,
                event.linkUrl()
        );
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
