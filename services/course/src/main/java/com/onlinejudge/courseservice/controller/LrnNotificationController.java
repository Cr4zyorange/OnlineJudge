package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.learning.LrnNotificationService;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.ApiResponse;
import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class LrnNotificationController {
    private final LrnNotificationService notifications;
    private final String internalToken;

    public LrnNotificationController(LrnNotificationService notifications,
                                     @Value("${onlinejudge.notifications.internal-token:}") String internalToken) {
        this.notifications = notifications;
        this.internalToken = internalToken;
    }

    @GetMapping
    public ApiResponse<LrnNotificationService.NotificationPage> list(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notifications.list(user.id(),
                new LrnNotificationService.NotificationQuery(type, isRead, startTime, endTime, page, size)));
    }

    @PutMapping("/read")
    public ApiResponse<LrnNotificationService.NotificationMutationResult> markRead(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @RequestBody LrnNotificationService.NotificationReadRequest request) {
        return ApiResponse.ok(notifications.markRead(user.id(), request));
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<LrnNotificationService.NotificationMutationResult> delete(
            @RequestAttribute("course.currentUser") CurrentUser user,
            @PathVariable long notificationId) {
        return ApiResponse.ok(notifications.delete(user.id(), notificationId));
    }

    /** Legacy synchronous event entry kept for existing callers; the primary path is the Rabbit inbox. */
    @PostMapping("/events")
    public ApiResponse<LrnNotificationService.NotificationEventResult> receiveEvent(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody LrnNotificationService.NotificationEventRequest request) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            throw new CourseException(HttpStatus.FORBIDDEN, "NOTIFICATION_EVENT_FORBIDDEN", "无权投递通知事件", false);
        }
        return ApiResponse.ok(notifications.receiveEvent(request));
    }
}
