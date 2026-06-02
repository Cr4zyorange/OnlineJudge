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
        String languageLimitJson,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        String outputCompareMode,
        List<HomeworkQuestionResponse> questions,
        List<HomeworkTestCaseResponse> testCases
) {
    static HomeworkResponse from(Homework homework) {
        return fromTeacherView(homework);
    }

    static HomeworkResponse fromTeacherView(Homework homework) {
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
                languageLimitJson(homework),
                timeLimitMs(homework),
                memoryLimitKb(homework),
                outputCompareMode(homework),
                homework.questions().stream().map(HomeworkQuestionResponse::from).toList(),
                homework.testCases().stream().map(HomeworkTestCaseResponse::from).toList()
        );
    }

    static HomeworkResponse fromStudentView(Homework homework) {
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
                languageLimitJson(homework),
                null,
                null,
                null,
                homework.questions().stream().map(HomeworkQuestionResponse::fromStudentView).toList(),
                homework.testCases().stream()
                        .filter(testCase -> !testCase.hidden())
                        .map(HomeworkTestCaseResponse::fromStudentView)
                        .toList()
        );
    }

    static HomeworkResponse summary(Homework homework) {
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
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private static String languageLimitJson(Homework homework) {
        return homework.judgeConfig() == null ? null : homework.judgeConfig().languageLimitJson();
    }

    private static Integer timeLimitMs(Homework homework) {
        return homework.judgeConfig() == null ? null : homework.judgeConfig().timeLimitMs();
    }

    private static Integer memoryLimitKb(Homework homework) {
        return homework.judgeConfig() == null ? null : homework.judgeConfig().memoryLimitKb();
    }

    private static String outputCompareMode(Homework homework) {
        return homework.judgeConfig() == null ? null : homework.judgeConfig().outputCompareMode();
    }
}
