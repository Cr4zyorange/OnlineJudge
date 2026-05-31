package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.HomeworkQuestion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record HomeworkQuestionPayload(
        @NotBlank String questionType,
        @NotBlank String stem,
        String optionsJson,
        @NotBlank String answerJson,
        @Min(1) int score,
        int sortOrder
) {
    HomeworkQuestion toDomain() {
        LocalDateTime now = LocalDateTime.now();
        return new HomeworkQuestion(0L, 0L, questionType, stem, optionsJson, answerJson, score, sortOrder, now, now);
    }
}
