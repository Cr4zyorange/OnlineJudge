package com.onlinejudge.lab.domain;

public record CreateLabSubmissionCommand(
        String code,
        String fileName,
        String fileContentType,
        byte[] fileBytes,
        String language
) {
}
