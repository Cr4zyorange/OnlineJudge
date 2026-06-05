package com.onlinejudge.lrn.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.lrn.service.ReminderRuleOverview;
import com.onlinejudge.lrn.service.ReminderRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reminder-rules")
public class ReminderRuleController {
    private final ReminderRuleService reminderRuleService;

    public ReminderRuleController(ReminderRuleService reminderRuleService) {
        this.reminderRuleService = reminderRuleService;
    }

    @GetMapping
    public ApiResponse<ReminderRuleOverview> getReminderRules(CurrentUser currentUser) {
        return ApiResponse.ok(reminderRuleService.getOverview(currentUser.id()));
    }

    @PutMapping
    public ApiResponse<ReminderRuleOverview> saveReminderRules(
            CurrentUser currentUser,
            @RequestBody ReminderRuleOverview request
    ) {
        return ApiResponse.ok(reminderRuleService.saveOverview(currentUser.id(), request));
    }
}
