package com.onlinejudge.courseservice.learning;

import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Course-owned reminder rule/setting facts (LRN folded into Course, #355). */
@Service
public class LrnReminderService {
    private final LrnReminderRepository reminders;

    public LrnReminderService(LrnReminderRepository reminders) { this.reminders = reminders; }

    public ReminderRuleOverview getOverview(long userId) {
        List<ReminderRuleItem> rules = reminders.listRules(userId).stream()
                .map(row -> new ReminderRuleItem(row.reminderType(), row.sourceModule(), row.aheadMinutes(),
                        row.enabled(), row.required()))
                .toList();
        if (rules.isEmpty()) {
            rules = List.of(new ReminderRuleItem("HOMEWORK_DEADLINE", "HWK", 1440, true, false),
                    new ReminderRuleItem("EXPERIMENT_DEADLINE", "LAB", 1440, true, false));
        }
        LrnReminderRepository.SettingRow setting = reminders.getSetting(userId).orElse(LrnReminderRepository.SettingRow.defaults());
        return new ReminderRuleOverview(rules, new NotificationSettingItem(setting.enableExperiment(), setting.enableHomework(),
                setting.enableGrade(), setting.enableAnnouncement(), setting.enableNonCriticalReminder()));
    }

    @Transactional
    public ReminderRuleOverview saveOverview(long userId, ReminderRuleOverview request) {
        if (request == null) throw new CourseException(HttpStatus.BAD_REQUEST, "REMINDER_RULE_INVALID", "提醒规则不能为空", false);
        List<LrnReminderRepository.RuleRow> rules = request.rules() == null ? List.of() : request.rules().stream()
                .map(item -> {
                    String type = item.reminderType().trim().toUpperCase(Locale.ROOT);
                    String module = item.sourceModule().trim().toUpperCase(Locale.ROOT);
                    if (!List.of("HOMEWORK_DEADLINE", "EXPERIMENT_DEADLINE").contains(type)
                            || !List.of("HWK", "LAB").contains(module) || item.aheadMinutes() < 0) {
                        throw new CourseException(HttpStatus.BAD_REQUEST, "REMINDER_RULE_INVALID", "提醒规则不合法", false);
                    }
                    return new LrnReminderRepository.RuleRow(type, module, item.aheadMinutes(), item.enabled(), item.required());
                })
                .toList();
        reminders.replaceRules(userId, rules);
        if (request.settings() != null) {
            reminders.saveSetting(userId, new LrnReminderRepository.SettingRow(
                    request.settings().enableExperiment(), request.settings().enableHomework(), request.settings().enableGrade(),
                    request.settings().enableAnnouncement(), request.settings().enableNonCriticalReminder()));
        }
        return getOverview(userId);
    }

    public record ReminderRuleOverview(List<ReminderRuleItem> rules, NotificationSettingItem settings) { }
    public record ReminderRuleItem(String reminderType, String sourceModule, int aheadMinutes, boolean enabled, boolean required) { }
    public record NotificationSettingItem(boolean enableExperiment, boolean enableHomework, boolean enableGrade,
                                          boolean enableAnnouncement, boolean enableNonCriticalReminder) { }
}
