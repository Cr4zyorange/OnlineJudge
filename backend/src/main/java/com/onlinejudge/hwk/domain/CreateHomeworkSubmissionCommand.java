package com.onlinejudge.hwk.domain;

import java.util.List;

public record CreateHomeworkSubmissionCommand(
        String answerText,
        String answerJson,
        List<String> fileIds,
        String codeText,
        String language
) {
}
