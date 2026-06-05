package com.onlinejudge.lrn.service;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.lrn.repository.JdbcReminderRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReminderRuleService {
    private static final Set<String> SUPPORTED_RULE_TYPES = Set.of("HOMEWORK_DEADLINE", "EXPERIMENT_DEADLINE");
    private static final Set<String> SUPPORTED_SOURCE_MODULES = Set.of("HWK", "LAB");
    private static final Set<Integer> SUPPORTED_AHEAD_MINUTES = Set.of(60, 1440);
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcReminderRuleRepository reminderRuleRepository;
    private final NotificationService notificationService;

    public ReminderRuleService(
            JdbcReminderRuleRepository reminderRuleRepository,
            NotificationService notificationService
    ) {
        this.reminderRuleRepository = reminderRuleRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReminderRuleOverview getOverview(long userId) {
        ensureDefaults(userId);
        return new ReminderRuleOverview(
                reminderRuleRepository.findRules(userId),
                reminderRuleRepository.findSetting(userId).orElse(defaultSetting())
        );
    }

    @Transactional
    public ReminderRuleOverview saveOverview(long userId, ReminderRuleOverview request) {
        if (request == null) {
            throw badRequest("提醒规则请求不能为空");
        }
        List<ReminderRuleItem> rules = normalizeRules(request.rules());
        NotificationSettingItem setting = request.settings() == null ? defaultSetting() : request.settings();
        reminderRuleRepository.upsertSetting(userId, setting);
        for (ReminderRuleItem rule : rules) {
            reminderRuleRepository.upsertRule(userId, rule);
        }
        ensureDefaults(userId);
        return getOverview(userId);
    }

    public int scanDeadlineReminders(LocalDateTime now) {
        LocalDateTime scanStartedAt = now == null ? LocalDateTime.now() : now;
        String batchId = "lrn-reminder-" + UUID.randomUUID();
        int createdCount = 0;
        try {
            List<Long> userIds = reminderRuleRepository.findUsersWithUpcomingDeadlineTasks(
                    scanStartedAt,
                    scanStartedAt.plusMinutes(1440)
            );
            for (Long userId : userIds) {
                createdCount += scanUserDeadlineReminders(userId, scanStartedAt);
            }
            reminderRuleRepository.insertScanLog(
                    batchId,
                    scanStartedAt,
                    LocalDateTime.now(),
                    createdCount,
                    null,
                    "NONE"
            );
            return createdCount;
        } catch (RuntimeException exception) {
            reminderRuleRepository.insertScanLog(
                    batchId,
                    scanStartedAt,
                    LocalDateTime.now(),
                    createdCount,
                    exception.getMessage(),
                    "FAILED"
            );
            throw exception;
        }
    }

    private int scanUserDeadlineReminders(long userId, LocalDateTime now) {
        ReminderRuleOverview overview = getOverview(userId);
        NotificationSettingItem setting = overview.settings();
        if (!setting.enableNonCriticalReminder()) {
            return 0;
        }
        int createdCount = 0;
        for (ReminderRuleItem rule : overview.rules()) {
            if (!rule.enabled()) {
                continue;
            }
            if ("HWK".equals(rule.sourceModule()) && !setting.enableHomework()) {
                continue;
            }
            if ("LAB".equals(rule.sourceModule()) && !setting.enableExperiment()) {
                continue;
            }
            LocalDateTime windowStart = reminderWindowStart(rule, now);
            LocalDateTime windowEnd = now.plusMinutes(rule.aheadMinutes());
            List<ReminderTaskTarget> targets = "HWK".equals(rule.sourceModule())
                    ? reminderRuleRepository.findHomeworkTargets(userId, windowStart, windowEnd)
                    : reminderRuleRepository.findLabTargets(userId, windowStart, windowEnd);
            for (ReminderTaskTarget target : targets) {
                createdCount += createDeadlineReminder(rule, target);
            }
        }
        return createdCount;
    }

    private int createDeadlineReminder(ReminderRuleItem rule, ReminderTaskTarget target) {
        NotificationEventResult result = notificationService.createNotifications(new NotificationCreateCommand(
                idempotencyKey(rule, target),
                rule.reminderType(),
                "LEARNING_REMINDER",
                target.courseId(),
                target.sourceModule(),
                target.sourceId(),
                List.of(target.userId()),
                reminderTitle(rule, target),
                reminderContent(rule, target),
                rule.aheadMinutes() <= 60 ? 3 : 2,
                target.actionUrl()
        ));
        return result.createdCount();
    }

    private LocalDateTime reminderWindowStart(ReminderRuleItem rule, LocalDateTime now) {
        if (rule.aheadMinutes() == 1440) {
            return now.plusMinutes(60);
        }
        return now;
    }

    private String idempotencyKey(ReminderRuleItem rule, ReminderTaskTarget target) {
        return "deadline-reminder:%d:%s:%d:%d".formatted(
                target.userId(),
                target.sourceModule(),
                target.sourceId(),
                rule.aheadMinutes()
        );
    }

    private String reminderTitle(ReminderRuleItem rule, ReminderTaskTarget target) {
        String typeLabel = "HWK".equals(target.sourceModule()) ? "作业" : "实验";
        String aheadLabel = rule.aheadMinutes() == 1440 ? "24小时" : "1小时";
        return "%s截止提醒：%s".formatted(typeLabel, target.title()) + "（提前" + aheadLabel + "）";
    }

    private String reminderContent(ReminderRuleItem rule, ReminderTaskTarget target) {
        String typeLabel = "HWK".equals(target.sourceModule()) ? "作业" : "实验";
        String deadline = target.deadline() == null ? "未设置" : target.deadline().format(DISPLAY_TIME);
        return "你有%s即将截止，请在 %s 前完成提交。提醒提前量：%d 分钟。"
                .formatted(typeLabel, deadline, rule.aheadMinutes());
    }

    private void ensureDefaults(long userId) {
        reminderRuleRepository.upsertSetting(userId, reminderRuleRepository.findSetting(userId).orElse(defaultSetting()));
        Map<String, ReminderRuleItem> existing = new LinkedHashMap<>();
        for (ReminderRuleItem rule : reminderRuleRepository.findRules(userId)) {
            existing.put(ruleKey(rule), rule);
        }
        for (ReminderRuleItem defaultRule : defaultRules()) {
            existing.putIfAbsent(ruleKey(defaultRule), defaultRule);
        }
        for (ReminderRuleItem rule : existing.values()) {
            reminderRuleRepository.upsertRule(userId, rule);
        }
    }

    private List<ReminderRuleItem> normalizeRules(List<ReminderRuleItem> requestRules) {
        List<ReminderRuleItem> sourceRules = requestRules == null || requestRules.isEmpty()
                ? defaultRules()
                : requestRules;
        Map<String, ReminderRuleItem> normalized = new LinkedHashMap<>();
        for (ReminderRuleItem rule : sourceRules) {
            if (rule == null) {
                throw badRequest("提醒规则不能为空");
            }
            String reminderType = normalize(rule.reminderType(), "提醒类型不能为空");
            String sourceModule = normalize(rule.sourceModule(), "来源模块不能为空");
            if (!SUPPORTED_RULE_TYPES.contains(reminderType) || !SUPPORTED_SOURCE_MODULES.contains(sourceModule)) {
                throw badRequest("提醒规则类型不合法");
            }
            if ("HOMEWORK_DEADLINE".equals(reminderType) && !"HWK".equals(sourceModule)) {
                throw badRequest("作业提醒必须关联 HWK");
            }
            if ("EXPERIMENT_DEADLINE".equals(reminderType) && !"LAB".equals(sourceModule)) {
                throw badRequest("实验提醒必须关联 LAB");
            }
            if (!SUPPORTED_AHEAD_MINUTES.contains(rule.aheadMinutes())) {
                throw badRequest("提醒提前量仅支持 60 或 1440 分钟");
            }
            ReminderRuleItem normalizedRule = new ReminderRuleItem(
                    reminderType,
                    sourceModule,
                    rule.aheadMinutes(),
                    rule.required() || rule.enabled(),
                    rule.required()
            );
            normalized.put(ruleKey(normalizedRule), normalizedRule);
        }
        for (ReminderRuleItem defaultRule : defaultRules()) {
            normalized.putIfAbsent(ruleKey(defaultRule), defaultRule);
        }
        return normalized.values().stream()
                .sorted(Comparator
                        .comparing(ReminderRuleItem::sourceModule).reversed()
                        .thenComparing(ReminderRuleItem::reminderType)
                        .thenComparing(ReminderRuleItem::aheadMinutes, Comparator.reverseOrder()))
                .toList();
    }

    private List<ReminderRuleItem> defaultRules() {
        return List.of(
                new ReminderRuleItem("HOMEWORK_DEADLINE", "HWK", 1440, true, false),
                new ReminderRuleItem("HOMEWORK_DEADLINE", "HWK", 60, true, false),
                new ReminderRuleItem("EXPERIMENT_DEADLINE", "LAB", 1440, true, false),
                new ReminderRuleItem("EXPERIMENT_DEADLINE", "LAB", 60, true, false)
        );
    }

    private NotificationSettingItem defaultSetting() {
        return new NotificationSettingItem(true, true, true, true, true);
    }

    private String ruleKey(ReminderRuleItem rule) {
        return rule.reminderType() + ":" + rule.sourceModule() + ":" + rule.aheadMinutes();
    }

    private String normalize(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private ApiException badRequest(String message) {
        return new ApiException("LRN-400-06", message, HttpStatus.BAD_REQUEST);
    }
}
