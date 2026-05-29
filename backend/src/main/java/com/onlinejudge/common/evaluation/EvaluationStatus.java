package com.onlinejudge.common.evaluation;

public enum EvaluationStatus {
    NONE,
    PENDING,
    RUNNING,
    ACCEPTED,
    WRONG_ANSWER,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    SYSTEM_ERROR
}
