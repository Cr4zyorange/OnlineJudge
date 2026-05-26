package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.onlinejudge.lab.domain.LabTestcase;

import java.time.LocalDateTime;

public record LabTestcaseResponse(
        long id,
        long labId,
        String input,
        String expectedOutput,
        int scoreWeight,
        @JsonProperty("public")
        boolean isPublic,
        int timeLimitMs,
        int memoryLimitKb,
        int orderNum,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static LabTestcaseResponse fromTeacherView(LabTestcase testcase) {
        return new LabTestcaseResponse(
                testcase.id(),
                testcase.labId(),
                testcase.input(),
                testcase.expectedOutput(),
                testcase.scoreWeight(),
                testcase.isPublic(),
                testcase.timeLimitMs(),
                testcase.memoryLimitKb(),
                testcase.orderNum(),
                testcase.deleted(),
                testcase.createdAt(),
                testcase.updatedAt()
        );
    }

    static LabTestcaseResponse fromStudentView(LabTestcase testcase) {
        return new LabTestcaseResponse(
                testcase.id(),
                testcase.labId(),
                testcase.input(),
                testcase.isPublic() ? testcase.expectedOutput() : null,
                testcase.scoreWeight(),
                testcase.isPublic(),
                testcase.timeLimitMs(),
                testcase.memoryLimitKb(),
                testcase.orderNum(),
                testcase.deleted(),
                testcase.createdAt(),
                testcase.updatedAt()
        );
    }
}
