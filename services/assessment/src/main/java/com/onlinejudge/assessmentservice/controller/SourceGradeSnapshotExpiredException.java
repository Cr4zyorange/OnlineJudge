package com.onlinejudge.assessmentservice.controller;

/** A caller must restart from the latest snapshot; returning an empty page would erase Grade's projection. */
final class SourceGradeSnapshotExpiredException extends RuntimeException {
    SourceGradeSnapshotExpiredException() { super("source-grade snapshot is no longer retained"); }
}
