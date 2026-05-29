package com.onlinejudge.lab.controller;

import com.onlinejudge.lab.domain.CreateLabExperimentCommand;
import com.onlinejudge.lab.domain.LabEvaluationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateLabExperimentRequest(
        Long chapterId,
        @NotBlank(message = "实验名称不能为空")
        String title,
        @NotBlank(message = "实验说明不能为空")
        String description,
        @NotNull(message = "截止时间必须填写")
        LocalDateTime deadline,
        @Min(value = 1, message = "满分必须大于 0")
        int maxScore,
        List<Long> attachmentIds,
        String allowedLanguages,
        @NotNull(message = "评测方式不能为空")
        LabEvaluationMode evaluationMode,
        boolean autoEvaluate,
        boolean reportRequired,
        @Min(value = 1, message = "时间限制必须大于 0")
        int timeLimitMs,
        @Min(value = 1, message = "内存限制必须大于 0")
        int memoryLimitKb,
        List<@Valid LabTestcasePayload> testcases
) {
    public CreateLabExperimentCommand toCommand() {
        return new CreateLabExperimentCommand(
                chapterId,
                title,
                description,
                deadline,
                maxScore,
                attachmentIds == null ? List.of() : attachmentIds,
                allowedLanguages,
                evaluationMode,
                autoEvaluate,
                reportRequired,
                timeLimitMs,
                memoryLimitKb,
                testcases == null ? List.of() : testcases.stream().map(LabTestcasePayload::toDraft).toList()
        );
    }
}
