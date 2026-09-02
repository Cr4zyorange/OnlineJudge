package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.learning.LrnReminderService;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reminder-rules")
public class LrnReminderController {
    private final LrnReminderService reminders;

    public LrnReminderController(LrnReminderService reminders) { this.reminders = reminders; }

    @GetMapping
    public ApiResponse<LrnReminderService.ReminderRuleOverview> get(
            @RequestAttribute("course.currentUser") CurrentUser user) {
        return ApiResponse.ok(reminders.getOverview(user.id()));
    }

    @PutMapping
    public ApiResponse<LrnReminderService.ReminderRuleOverview> save(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestBody LrnReminderService.ReminderRuleOverview request) {
        return ApiResponse.ok(reminders.saveOverview(user.id(), request));
    }
}
