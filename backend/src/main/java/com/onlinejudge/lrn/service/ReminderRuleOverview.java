package com.onlinejudge.lrn.service;

import java.util.List;

public record ReminderRuleOverview(
        List<ReminderRuleItem> rules,
        NotificationSettingItem settings
) {
}
