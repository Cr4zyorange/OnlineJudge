package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkQuestionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record HomeworkQuestionPayload(
        @NotNull(message = "题目类型不能为空")
        HomeworkQuestionType questionType,
        @NotBlank(message = "题干不能为空")
        String stem,
        @NotBlank(message = "选项不能为空")
        String optionsJson,
        @NotBlank(message = "答案不能为空")
        String answerJson,
        @NotNull(message = "题目分值不能为空")
        @DecimalMin(value = "0.00", message = "题目分值不能为负数")
        BigDecimal score,
        int sortOrder
) {
    HomeworkQuestion toDomain() {
        return new HomeworkQuestion(0L, 0L, questionType, stem, optionsJson, answerJson, score, sortOrder);
    }
}
