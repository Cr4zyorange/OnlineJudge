package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record HomeworkTestCasePayload(
        @NotNull(message = "测试输入不能缺失")
        String inputData,
        @NotBlank(message = "期望输出不能为空")
        String expectedOutput,
        @NotNull(message = "测试用例权重不能为空")
        @DecimalMin(value = "0.00", message = "测试用例权重不能为负数")
        BigDecimal scoreWeight,
        @JsonAlias("isHidden")
        boolean hidden,
        @Min(value = 1, message = "时间限制必须大于 0")
        int timeLimitMs,
        @Min(value = 1, message = "内存限制必须大于 0")
        int memoryLimitKb,
        int sortOrder
) {
    HomeworkTestCase toDomain() {
        return new HomeworkTestCase(
                0L,
                0L,
                inputData,
                expectedOutput,
                scoreWeight,
                hidden,
                timeLimitMs,
                memoryLimitKb,
                sortOrder
        );
    }
}
