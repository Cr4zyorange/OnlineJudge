package com.onlinejudge.assessmentservice.model;

/** Durable queue states, deliberately separate from LAB/HWK evaluator result statuses. */
public enum TaskState { PENDING, RUNNING, SUCCEEDED, FAILED }
