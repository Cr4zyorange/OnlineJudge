package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;
import java.util.List;

public record CreateHomeworkCommand(
        long courseId,
        Long chapterId,
        String title,
        String description,
        HomeworkType type,
        LocalDateTime deadline,
        int totalScore,
        boolean allowResubmit,
        boolean allowLateSubmit,
        boolean showEvaluationBeforePublish,
        List<HomeworkQuestion> questions,
        List<HomeworkTestCase> testCases,
        HomeworkJudgeConfig judgeConfig
) {
}
