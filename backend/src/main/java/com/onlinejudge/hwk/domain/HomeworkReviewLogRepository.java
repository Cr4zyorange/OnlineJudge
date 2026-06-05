package com.onlinejudge.hwk.domain;

import java.util.List;

public interface HomeworkReviewLogRepository {
    HomeworkReviewLog save(HomeworkReviewLog reviewLog);

    List<HomeworkReviewLog> findBySubmissionId(long submissionId);
}
