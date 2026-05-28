package com.onlinejudge.hwk.service;

public record SubmitHomeworkCommand(
        String answerText,
        String answerJson,
        String fileUrl,
        String codeText,
        String language
) {
}
