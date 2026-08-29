package com.onlinejudge.integration.learning;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LearningAssessmentClient {
    List<LearningCourseClient.ExternalTask> findTasks(long userId, Collection<Long> courseIds);
    List<DeadlineTask> findUpcomingTasks(long userId, Collection<Long> courseIds,
                                         LocalDateTime windowStart, LocalDateTime windowEnd, String sourceModule);

    record DeadlineTask(long courseId, long sourceId, String sourceModule, String title, LocalDateTime deadline) {}
}
