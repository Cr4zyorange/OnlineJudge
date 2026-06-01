package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.CreateHomeworkCommand;
import com.onlinejudge.hwk.domain.HomeworkJudgeConfig;
import com.onlinejudge.hwk.domain.HomeworkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record HomeworkRequest(
        @Min(1) long courseId,
        Long chapterId,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull HomeworkType type,
        @NotNull LocalDateTime deadline,
        @Min(1) int totalScore,
        boolean allowResubmit,
        boolean allowLateSubmit,
        boolean showEvaluationBeforePublish,
        List<@Valid HomeworkQuestionPayload> questions,
        List<@Valid HomeworkTestCasePayload> testCases,
        String languageLimitJson,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        String outputCompareMode
) {
    CreateHomeworkCommand toCommand() {
        return new CreateHomeworkCommand(
                courseId,
                chapterId,
                title,
                description,
                type,
                deadline,
                totalScore,
                allowResubmit,
                allowLateSubmit,
                showEvaluationBeforePublish,
                questions == null ? List.of() : questions.stream().map(HomeworkQuestionPayload::toDomain).toList(),
                testCases == null ? List.of() : testCases.stream().map(HomeworkTestCasePayload::toDomain).toList(),
                judgeConfig()
        );
    }

    private HomeworkJudgeConfig judgeConfig() {
        if (type != HomeworkType.CODE) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return new HomeworkJudgeConfig(
                0L,
                0L,
                languageLimitJson,
                timeLimitMs == null ? 1000 : timeLimitMs,
                memoryLimitKb == null ? 65536 : memoryLimitKb,
                outputCompareMode == null || outputCompareMode.isBlank() ? "EXACT" : outputCompareMode,
                now,
                now
        );
    }
}
