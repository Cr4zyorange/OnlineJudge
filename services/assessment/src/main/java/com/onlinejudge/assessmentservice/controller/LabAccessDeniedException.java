package com.onlinejudge.assessmentservice.controller;

/** Global role denial for a LAB asset: do not disclose the asset before authorization. */
final class LabAccessDeniedException extends RuntimeException {
    LabAccessDeniedException(String message) { super(message); }
}
