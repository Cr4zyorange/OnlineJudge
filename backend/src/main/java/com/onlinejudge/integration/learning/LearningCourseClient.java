package com.onlinejudge.integration.learning;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface LearningCourseClient {
    boolean isActiveMember(long userId, long courseId);
    boolean canManage(long userId, long courseId);
    boolean chapterBelongs(long chapterId, long courseId);
    List<StudentMembership> findActiveStudents(long courseId);
    List<StudentMembership> findAllActiveStudents();
    List<Long> findActiveCourseIds(long userId);
    Map<Long, String> findCourseNames(Collection<Long> courseIds);
    Map<Long, String> findChapterNames(Collection<Long> chapterIds);
    Map<Long, Integer> findChapterSortOrders(Collection<Long> chapterIds);
    List<ExternalTask> findResourceTasks(long userId);

    record StudentMembership(long userId, long courseId, String courseName) {}
    record ExternalTask(long id, long courseId, String courseName, String sourceModule, long sourceId,
                        String taskType, String title, LocalDateTime deadline, String actionUrl,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
