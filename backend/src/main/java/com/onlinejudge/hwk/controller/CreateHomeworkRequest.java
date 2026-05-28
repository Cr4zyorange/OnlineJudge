package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.hwk.service.CreateHomeworkCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateHomeworkRequest(
        @Positive(message = "课程编号不能为空")
        long courseId,
        Long chapterId,
        @NotBlank(message = "作业标题不能为空")
        String title,
        @NotBlank(message = "作业说明不能为空")
        String description,
        @NotNull(message = "作业类型不能为空")
        HomeworkType type,
        @NotNull(message = "满分不能为空")
        @DecimalMin(value = "0.01", message = "满分必须大于 0")
        BigDecimal totalScore,
        @NotNull(message = "截止时间不能为空")
        @Future(message = "截止时间必须晚于当前时间")
        LocalDateTime deadline,
        boolean allowResubmit,
        boolean allowLateSubmit,
        boolean showEvaluationBeforePublish,
        @Valid
        List<HomeworkQuestionPayload> questions,
        @Valid
        List<HomeworkTestCasePayload> testCases
) {
    CreateHomeworkCommand toCommand() {
        return new CreateHomeworkCommand(
                courseId,
                chapterId,
                title,
                description,
                type,
                totalScore,
                deadline,
                allowResubmit,
                allowLateSubmit,
                showEvaluationBeforePublish,
                questions == null ? List.of() : questions.stream().map(HomeworkQuestionPayload::toDomain).toList(),
                testCases == null ? List.of() : testCases.stream().map(HomeworkTestCasePayload::toDomain).toList()
        );
    }
}
