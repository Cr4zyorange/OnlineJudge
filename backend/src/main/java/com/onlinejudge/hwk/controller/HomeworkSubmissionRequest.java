package com.onlinejudge.hwk.controller;

public record HomeworkSubmissionRequest(
        String answerText,
        String answerJson,
        String fileUrl,
        String codeText,
        String language
) {
}
