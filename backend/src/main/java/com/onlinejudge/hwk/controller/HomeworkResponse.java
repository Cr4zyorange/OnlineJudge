package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkType;

import java.math.BigDecimal;
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
        BigDecimal totalScore,
        LocalDateTime deadline,
        boolean allowResubmit,
        boolean allowLateSubmit,
        boolean showEvaluationBeforePublish,
        long createdBy,
        LocalDateTime publishedAt,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<HomeworkQuestionResponse> questions,
        List<HomeworkTestCaseResponse> testCases
) {
    static HomeworkResponse fromTeacherView(Homework homework) {
        return from(homework, true);
    }

    static HomeworkResponse fromStudentView(Homework homework) {
        return from(homework, false);
    }

    private static HomeworkResponse from(Homework homework, boolean teacherView) {
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
                homework.createdBy(),
                homework.publishedAt(),
                homework.deleted(),
                homework.createdAt(),
                homework.updatedAt(),
                homework.questions().stream()
                        .map(question -> HomeworkQuestionResponse.from(question, teacherView))
                        .toList(),
                homework.testCases().stream()
                        .map(testCase -> HomeworkTestCaseResponse.from(testCase, teacherView))
                        .toList()
        );
    }
}
