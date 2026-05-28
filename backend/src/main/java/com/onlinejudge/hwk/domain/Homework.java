package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Homework(
        long id,
        long courseId,
        Long chapterId,
        String title,
        String description,
        HomeworkType type,
        HomeworkStatus status,
        BigDecimal totalScore,
        LocalDateTime deadline,
        boolean allowResubmit,
        boolean allowLateSubmit,
        boolean showEvaluationBeforePublish,
        Long judgeConfigId,
        long createdBy,
        LocalDateTime publishedAt,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<HomeworkQuestion> questions,
        List<HomeworkTestCase> testCases
) {
    public Homework withChildren(List<HomeworkQuestion> questions, List<HomeworkTestCase> testCases) {
        return new Homework(
                id,
                courseId,
                chapterId,
                title,
                description,
                type,
                status,
                totalScore,
                deadline,
                allowResubmit,
                allowLateSubmit,
                showEvaluationBeforePublish,
                judgeConfigId,
                createdBy,
                publishedAt,
                deleted,
                createdAt,
                updatedAt,
                List.copyOf(questions),
                List.copyOf(testCases)
        );
    }

    public Homework publish(LocalDateTime now) {
        return new Homework(
                id,
                courseId,
                chapterId,
                title,
                description,
                type,
                HomeworkStatus.PUBLISHED,
                totalScore,
                deadline,
                allowResubmit,
                allowLateSubmit,
                showEvaluationBeforePublish,
                judgeConfigId,
                createdBy,
                now,
                deleted,
                createdAt,
                now,
                questions,
                testCases
        );
    }

    public Homework close(LocalDateTime now) {
        return new Homework(
                id,
                courseId,
                chapterId,
                title,
                description,
                type,
                HomeworkStatus.CLOSED,
                totalScore,
                deadline,
                allowResubmit,
                allowLateSubmit,
                showEvaluationBeforePublish,
                judgeConfigId,
                createdBy,
                publishedAt,
                deleted,
                createdAt,
                now,
                questions,
                testCases
        );
    }
}
