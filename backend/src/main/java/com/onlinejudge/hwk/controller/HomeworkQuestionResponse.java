package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.hwk.domain.HomeworkQuestion;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeworkQuestionResponse(
        long id,
        long homeworkId,
        String questionType,
        String stem,
        String optionsJson,
        String answerJson,
        int score,
        int sortOrder
) {
    static HomeworkQuestionResponse from(HomeworkQuestion question) {
        return from(question, true);
    }

    static HomeworkQuestionResponse fromStudentView(HomeworkQuestion question) {
        return from(question, false);
    }

    private static HomeworkQuestionResponse from(HomeworkQuestion question, boolean includeAnswer) {
        return new HomeworkQuestionResponse(
                question.id(),
                question.homeworkId(),
                question.questionType(),
                question.stem(),
                question.optionsJson(),
                includeAnswer ? question.answerJson() : null,
                question.score(),
                question.sortOrder()
        );
    }
}
