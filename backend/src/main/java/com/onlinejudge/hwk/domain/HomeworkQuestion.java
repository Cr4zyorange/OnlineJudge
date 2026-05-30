package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;

public record HomeworkQuestion(
        long id,
        long homeworkId,
        String questionType,
        String stem,
        String optionsJson,
        String answerJson,
        int score,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public HomeworkQuestion withHomeworkId(long homeworkId) {
        return new HomeworkQuestion(
                id,
                homeworkId,
                questionType,
                stem,
                optionsJson,
                answerJson,
                score,
                sortOrder,
                createdAt,
                updatedAt
        );
    }
}
