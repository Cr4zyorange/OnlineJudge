package com.onlinejudge.lrn.service;

import com.onlinejudge.common.event.NotificationEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PersistentNotificationEventPublisherTest {

    @Test
    void requiredPublishPropagatesPersistenceFailureToSourceTransaction() {
        NotificationService notificationService = mock(NotificationService.class);
        IllegalStateException failure = new IllegalStateException("notification database unavailable");
        doThrow(failure).when(notificationService).createNotifications(any());
        PersistentNotificationEventPublisher publisher = new PersistentNotificationEventPublisher(notificationService);

        assertThatThrownBy(() -> publisher.publishRequired(homeworkPublishedEvent()))
                .isSameAs(failure);
    }

    @Test
    void ordinaryPublishKeepsBestEffortFailureSemantics() {
        NotificationService notificationService = mock(NotificationService.class);
        doThrow(new IllegalStateException("notification database unavailable"))
                .when(notificationService).createNotifications(any());
        PersistentNotificationEventPublisher publisher = new PersistentNotificationEventPublisher(notificationService);

        assertThatNoException().isThrownBy(() -> publisher.publish(homeworkPublishedEvent()));
    }

    private NotificationEvent homeworkPublishedEvent() {
        return new NotificationEvent(
                "homework-published-281",
                "HOMEWORK_PUBLISHED",
                101L,
                List.of(601L),
                "homework published",
                "New homework: transaction rollback",
                "HWK",
                281L,
                "/courses/101/homeworks/281",
                LocalDateTime.of(2026, 8, 26, 14, 0)
        );
    }
}
