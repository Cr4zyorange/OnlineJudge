package com.onlinejudge.hwk.domain;

import java.math.BigDecimal;

public record HomeworkQuestion(
        long id,
        long homeworkId,
        HomeworkQuestionType questionType,
        String stem,
        String optionsJson,
        String answerJson,
        BigDecimal score,
        int sortOrder
) {
    public HomeworkQuestion withHomeworkId(long homeworkId) {
        return new HomeworkQuestion(id, homeworkId, questionType, stem, optionsJson, answerJson, score, sortOrder);
    }
}
