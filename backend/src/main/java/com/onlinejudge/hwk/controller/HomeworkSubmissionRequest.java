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
        return new CreateHomeworkSubmissionCommand(answerText, answerJson, fileIds, codeText, language);
    }
}
