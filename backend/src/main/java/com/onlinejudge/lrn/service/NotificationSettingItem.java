package com.onlinejudge.lrn.service;

public record NotificationSettingItem(
        boolean enableExperiment,
        boolean enableHomework,
        boolean enableGrade,
        boolean enableAnnouncement,
        boolean enableNonCriticalReminder
) {
}
