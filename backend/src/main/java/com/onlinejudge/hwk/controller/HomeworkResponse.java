package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkType;

import java.time.LocalDateTime;
import java.util.List;

public record HomeworkResponse(
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
        List<HomeworkQuestionResponse> questions,
        List<HomeworkTestCaseResponse> testCases
) {
    static HomeworkResponse from(Homework homework) {
        return new HomeworkResponse(
                homework.id(),
                homework.courseId(),
                homework.chapterId(),
                homework.title(),
                homework.description(),
                homework.type(),
                homework.status(),
                homework.totalScore(),
                homework.deadline(),
                homework.allowResubmit(),
                homework.allowLateSubmit(),
                homework.showEvaluationBeforePublish(),
                homework.judgeConfigId(),
                homework.createdBy(),
                homework.publishedAt(),
                homework.deleted(),
                homework.createdAt(),
                homework.updatedAt(),
                homework.questions().stream().map(HomeworkQuestionResponse::from).toList(),
                homework.testCases().stream().map(HomeworkTestCaseResponse::from).toList()
        );
    }
}
