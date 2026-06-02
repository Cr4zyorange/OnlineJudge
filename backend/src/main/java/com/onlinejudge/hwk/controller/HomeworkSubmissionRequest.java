package com.onlinejudge.hwk.controller;

import com.onlinejudge.hwk.domain.CreateHomeworkSubmissionCommand;

import java.util.List;

public record HomeworkSubmissionRequest(
        String answerText,
        String answerJson,
        List<String> fileIds,
        String codeText,
        String language
) {
    CreateHomeworkSubmissionCommand toCommand() {
        return new CreateHomeworkSubmissionCommand(answerText, answerJson, normalizeFileIds(fileIds), codeText, language);
    }

    private static String normalizeFileIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return null;
        }
        String joined = fileIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        return joined == null || joined.isBlank() ? null : joined;
    }
}
