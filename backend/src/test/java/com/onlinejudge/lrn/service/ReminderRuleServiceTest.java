package com.onlinejudge.lrn.service;

import com.onlinejudge.lrn.repository.JdbcReminderRuleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderRuleServiceTest {
    @Test
    void failedDeadlineScanWritesFailureBatchLogBeforeRethrowing() {
        JdbcReminderRuleRepository reminderRuleRepository = mock(JdbcReminderRuleRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        ReminderRuleService reminderRuleService = new ReminderRuleService(reminderRuleRepository, notificationService);
        LocalDateTime now = LocalDateTime.of(2026, 6, 5, 9, 0);

        when(reminderRuleRepository.findUsersWithUpcomingDeadlineTasks(eq(now), eq(now.plusMinutes(1440))))
                .thenReturn(List.of(601L));
        when(reminderRuleRepository.findSetting(601L)).thenReturn(Optional.of(
                new NotificationSettingItem(true, true, true, true, true)
        ));
        when(reminderRuleRepository.findRules(601L)).thenReturn(List.of(
                new ReminderRuleItem("HOMEWORK_DEADLINE", "HWK", 60, true, false)
        ));
        when(reminderRuleRepository.findHomeworkTargets(eq(601L), eq(now), eq(now.plusMinutes(60))))
                .thenReturn(List.of(new ReminderTaskTarget(
                        601L,
                        101L,
                        501L,
                        "HWK",
                        "Deadline homework",
                        now.plusMinutes(55),
                        "/courses/101/homeworks/501?role=student"
                )));
        when(notificationService.createNotifications(any(NotificationCreateCommand.class)))
                .thenThrow(new IllegalStateException("notification persistence unavailable"));

        assertThatThrownBy(() -> reminderRuleService.scanDeadlineReminders(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification persistence unavailable");

        ArgumentCaptor<String> retryStatus = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> failedReason = ArgumentCaptor.forClass(String.class);
        verify(reminderRuleRepository).insertScanLog(
                any(String.class),
                eq(now),
                any(LocalDateTime.class),
                eq(0),
                failedReason.capture(),
                retryStatus.capture()
        );
        assertThat(failedReason.getValue()).contains("notification persistence unavailable");
        assertThat(retryStatus.getValue()).isEqualTo("FAILED");
    }
}
