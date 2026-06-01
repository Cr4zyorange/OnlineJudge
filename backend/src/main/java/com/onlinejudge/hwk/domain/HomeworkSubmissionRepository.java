package com.onlinejudge.hwk.domain;

import java.util.Optional;

public interface HomeworkSubmissionRepository {
    HomeworkSubmission save(HomeworkSubmission submission);

    HomeworkSubmission update(HomeworkSubmission submission);

    Optional<HomeworkSubmission> findLatestFinalByHomeworkIdAndStudentId(long homeworkId, long studentId);
}
