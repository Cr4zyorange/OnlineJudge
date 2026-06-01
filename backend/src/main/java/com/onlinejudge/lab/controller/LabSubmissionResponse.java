package com.onlinejudge.lab.controller;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmitStatus;

import java.time.LocalDateTime;

public record LabSubmissionResponse(
        long submissionId,
        long labId,
        long studentId,
        LabSubmitStatus submitStatus,
        EvaluationStatus evaluationStatus,
        Integer autoScore,
        int version,
        LocalDateTime submittedAt
) {
    public static LabSubmissionResponse from(LabSubmission submission) {
        return new LabSubmissionResponse(
                submission.id(),
                submission.labId(),
                submission.studentId(),
                submission.submitStatus(),
                submission.evaluationStatus(),
                submission.autoScore(),
                submission.version(),
                submission.submittedAt()
        );
    }
}
