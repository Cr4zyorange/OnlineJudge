package com.onlinejudge.hwk.domain;

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
        int totalScore,
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
        List<HomeworkTestCase> testCases,
        HomeworkJudgeConfig judgeConfig
) {
    public Homework update(
            Long chapterId,
            String title,
            String description,
            HomeworkType type,
            int totalScore,
            LocalDateTime deadline,
            boolean allowResubmit,
            boolean allowLateSubmit,
            boolean showEvaluationBeforePublish,
            LocalDateTime updatedAt,
            List<HomeworkQuestion> questions,
            List<HomeworkTestCase> testCases,
            HomeworkJudgeConfig judgeConfig
    ) {
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
                questions,
                testCases,
                judgeConfig
        );
    }

    public Homework withQuestions(List<HomeworkQuestion> questions, LocalDateTime updatedAt) {
        return new Homework(id, courseId, chapterId, title, description, type, status, totalScore, deadline,
                allowResubmit, allowLateSubmit, showEvaluationBeforePublish, judgeConfigId, createdBy, publishedAt,
                deleted, createdAt, updatedAt, questions, testCases, judgeConfig);
    }

    public Homework withTestCases(List<HomeworkTestCase> testCases, LocalDateTime updatedAt) {
        return new Homework(id, courseId, chapterId, title, description, type, status, totalScore, deadline,
                allowResubmit, allowLateSubmit, showEvaluationBeforePublish, judgeConfigId, createdBy, publishedAt,
                deleted, createdAt, updatedAt, questions, testCases, judgeConfig);
    }

    public Homework publish(LocalDateTime publishedAt) {
        return new Homework(id, courseId, chapterId, title, description, type, HomeworkStatus.PUBLISHED, totalScore,
                deadline, allowResubmit, allowLateSubmit, showEvaluationBeforePublish, judgeConfigId, createdBy,
                publishedAt, deleted, createdAt, publishedAt, questions, testCases, judgeConfig);
    }

    public Homework close(LocalDateTime updatedAt) {
        return new Homework(id, courseId, chapterId, title, description, type, HomeworkStatus.CLOSED, totalScore,
                deadline, allowResubmit, allowLateSubmit, showEvaluationBeforePublish, judgeConfigId, createdBy,
                publishedAt, deleted, createdAt, updatedAt, questions, testCases, judgeConfig);
    }

    public Homework publishScores(LocalDateTime updatedAt) {
        return new Homework(id, courseId, chapterId, title, description, type, HomeworkStatus.SCORE_PUBLISHED, totalScore,
                deadline, allowResubmit, allowLateSubmit, showEvaluationBeforePublish, judgeConfigId, createdBy,
                publishedAt, deleted, createdAt, updatedAt, questions, testCases, judgeConfig);
    }

    public Homework softDelete(LocalDateTime deletedAt) {
        return new Homework(id, courseId, chapterId, title, description, type, status, totalScore,
                deadline, allowResubmit, allowLateSubmit, showEvaluationBeforePublish, judgeConfigId, createdBy,
                publishedAt, true, createdAt, deletedAt, questions, testCases, judgeConfig);
    }
}
