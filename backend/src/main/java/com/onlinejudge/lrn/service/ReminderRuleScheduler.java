package com.onlinejudge.lrn.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "onlinejudge.lrn.reminders", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderRuleScheduler {
    private final ReminderRuleService reminderRuleService;

    public ReminderRuleScheduler(ReminderRuleService reminderRuleService) {
        this.reminderRuleService = reminderRuleService;
    }

    @Scheduled(cron = "${onlinejudge.lrn.reminders.scan-cron:0 */10 * * * *}")
    public void scanDeadlineReminders() {
        reminderRuleService.scanDeadlineReminders(LocalDateTime.now());
    }
}
