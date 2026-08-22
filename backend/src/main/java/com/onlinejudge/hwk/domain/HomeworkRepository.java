package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HomeworkRepository {
    Homework save(Homework homework);

    Optional<Homework> update(Homework homework);

    Optional<Homework> replaceQuestions(long homeworkId, List<HomeworkQuestion> questions);

    Optional<Homework> replaceTestCases(long homeworkId, List<HomeworkTestCase> testCases);

    boolean softDeleteDraft(long homeworkId, LocalDateTime deletedAt);

    Optional<Homework> findById(long homeworkId);

    Optional<Homework> findByIdForUpdate(long homeworkId);

    List<Homework> findByCourseId(long courseId, HomeworkStatus status, String keyword, int page, int size);

    long countByCourseId(long courseId, HomeworkStatus status, String keyword);

    List<Homework> findByCourseIdAndStatuses(long courseId, List<HomeworkStatus> statuses, String keyword, int page, int size);

    long countByCourseIdAndStatuses(long courseId, List<HomeworkStatus> statuses, String keyword);
}
