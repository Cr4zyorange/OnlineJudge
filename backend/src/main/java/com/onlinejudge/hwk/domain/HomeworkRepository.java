package com.onlinejudge.hwk.domain;

import java.util.List;
import java.util.Optional;

public interface HomeworkRepository {
    Homework save(Homework homework);

    Homework update(Homework homework);

    Homework replaceQuestions(long homeworkId, List<HomeworkQuestion> questions);

    Homework replaceTestCases(long homeworkId, List<HomeworkTestCase> testCases);

    Optional<Homework> findById(long homeworkId);

    List<Homework> findByCourseId(long courseId, HomeworkStatus status, String keyword, int page, int size);

    long countByCourseId(long courseId, HomeworkStatus status, String keyword);

    List<Homework> findByCourseIdAndStatuses(long courseId, List<HomeworkStatus> statuses, String keyword, int page, int size);

    long countByCourseIdAndStatuses(long courseId, List<HomeworkStatus> statuses, String keyword);
}
