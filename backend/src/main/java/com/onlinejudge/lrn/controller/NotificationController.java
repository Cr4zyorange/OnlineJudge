package com.onlinejudge.lrn.controller;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.lrn.service.NotificationEventRequest;
import com.onlinejudge.lrn.service.NotificationEventResult;
import com.onlinejudge.lrn.service.NotificationMutationResult;
import com.onlinejudge.lrn.service.NotificationPage;
import com.onlinejudge.lrn.service.NotificationQuery;
import com.onlinejudge.lrn.service.NotificationReadRequest;
import com.onlinejudge.lrn.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final String internalToken;

    public NotificationController(
            NotificationService notificationService,
            @Value("${onlinejudge.notifications.internal-token:}") String internalToken
    ) {
        this.notificationService = notificationService;
        this.internalToken = internalToken;
    }

    @GetMapping
    public ApiResponse<NotificationPage> listNotifications(
            CurrentUser currentUser,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(notificationService.listNotifications(
                currentUser.id(),
                new NotificationQuery(type, isRead, startTime, endTime, page, size)
        ));
    }

    @PutMapping("/read")
    public ApiResponse<NotificationMutationResult> markRead(
            CurrentUser currentUser,
            @RequestBody NotificationReadRequest request
    ) {
        return ApiResponse.ok(notificationService.markRead(currentUser.id(), request));
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<NotificationMutationResult> deleteNotification(
            CurrentUser currentUser,
            @PathVariable long notificationId
    ) {
        return ApiResponse.ok(notificationService.deleteNotification(currentUser.id(), notificationId));
    }

    @PostMapping("/events")
    public ApiResponse<NotificationEventResult> receiveEvent(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody NotificationEventRequest request
    ) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ApiException("LRN-403-04", "无权投递通知事件", HttpStatus.FORBIDDEN);
        }
        return ApiResponse.ok(notificationService.receiveEvent(request));
    }
}
