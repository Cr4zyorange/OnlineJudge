package com.onlinejudge.hwk.service;

import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateHomeworkCommand(
        long courseId,
        Long chapterId,
        String title,
        String description,
        HomeworkType type,
        BigDecimal totalScore,
        LocalDateTime deadline,
        boolean allowResubmit,
        boolean allowLateSubmit,
        boolean showEvaluationBeforePublish,
        List<HomeworkQuestion> questions,
        List<HomeworkTestCase> testCases
) {
}
