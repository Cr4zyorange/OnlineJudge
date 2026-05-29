package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.onlinejudge.lab.domain.LabTestcaseDraft;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LabTestcasePayload(
        @NotNull(message = "测试用例输入不能为空")
        String input,
        @NotNull(message = "测试用例输出不能为空")
        String expectedOutput,
        @Min(value = 0, message = "测试用例分值不能为负数")
        int scoreWeight,
        @JsonProperty("public")
        boolean isPublic,
        @Min(value = 1, message = "测试用例时间限制必须大于 0")
        int timeLimitMs,
        @Min(value = 1, message = "测试用例内存限制必须大于 0")
        int memoryLimitKb,
        @Min(value = 0, message = "测试用例排序必须为非负整数")
        int orderNum
) {
    public LabTestcaseDraft toDraft() {
        return new LabTestcaseDraft(
                input,
                expectedOutput,
                scoreWeight,
                isPublic,
                timeLimitMs,
                memoryLimitKb,
                orderNum
        );
    }
}
