package com.onlinejudge.hwk.domain;

public record CreateHomeworkSubmissionCommand(
        String answerText,
        String answerJson,
        String fileIds,
        String codeText,
        String language
) {
}
