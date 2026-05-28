package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onlinejudge.hwk.service.SubmitHomeworkCommand;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitHomeworkRequest(
        String answerText,
        String answerJson,
        String fileUrl,
        String codeText,
        String language
) {
    SubmitHomeworkCommand toCommand() {
        return new SubmitHomeworkCommand(answerText, answerJson, fileUrl, codeText, language);
    }
}
