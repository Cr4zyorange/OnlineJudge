package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkQuestionType;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkQuestionResponse(
        long id,
        HomeworkQuestionType questionType,
        String stem,
        String optionsJson,
        String answerJson,
        BigDecimal score,
        int sortOrder
) {
    static HomeworkQuestionResponse from(HomeworkQuestion question, boolean includeAnswer) {
        return new HomeworkQuestionResponse(
                question.id(),
                question.questionType(),
                question.stem(),
                question.optionsJson(),
                includeAnswer ? question.answerJson() : null,
                question.score(),
                question.sortOrder()
        );
    }
}
