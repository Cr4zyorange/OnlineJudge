package com.onlinejudge.lrn.service;

public record NotificationQuery(
        String type,
        Boolean isRead,
        String startTime,
        String endTime,
        Integer page,
        Integer size
) {
}
