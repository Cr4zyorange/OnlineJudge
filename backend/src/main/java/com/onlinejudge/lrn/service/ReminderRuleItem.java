package com.onlinejudge.lrn.service;

public record ReminderRuleItem(
        String reminderType,
        String sourceModule,
        int aheadMinutes,
        boolean enabled,
        boolean required
) {
}
