package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkQuestion;

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
        return new HomeworkQuestionResponse(
                question.id(),
                question.homeworkId(),
                question.questionType(),
                question.stem(),
                question.optionsJson(),
                question.answerJson(),
                question.score(),
                question.sortOrder()
        );
    }
}
