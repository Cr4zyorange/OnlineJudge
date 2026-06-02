package com.onlinejudge.hwk.domain;

import com.onlinejudge.common.web.PageResponse;

import java.util.List;
import java.util.Optional;

public interface HomeworkSubmissionRepository {
    HomeworkSubmission save(HomeworkSubmission submission);

    HomeworkSubmission update(HomeworkSubmission submission);

    Optional<HomeworkSubmission> findById(long submissionId);

    Optional<HomeworkSubmission> findLatestFinalByHomeworkIdAndStudentId(long homeworkId, long studentId);

    List<HomeworkSubmission> findByHomeworkIdAndStudentId(long homeworkId, long studentId);

    PageResponse<HomeworkSubmission> findByHomeworkId(
            long homeworkId,
            HomeworkSubmissionSearchCriteria criteria,
            int page,
            int size
    );
}
