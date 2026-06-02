package com.onlinejudge.crs.domain.dto;

import java.util.List;

public record CourseHomeSummaryResponse(
        CourseResponse course,
        List<AnnouncementResponse> announcements,
        List<CourseRecentTaskResponse> recentTasks
) {
}
