package com.onlinejudge.crs.service;

import com.onlinejudge.crs.repository.CourseEventOutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Source-owned recovery for Course rows that existed before reliable v2
 * membership events were enabled.  It only creates a durable Course snapshot
 * outbox fact; the ordinary confirmed publisher delivers it later.  Learning
 * is never called synchronously and cannot manufacture Course membership
 * truth.
 */
@Component
@ConditionalOnProperty(name = "onlinejudge.reliability.publisher.enabled", havingValue = "true")
public class CourseMembershipBootstrapper {
    private final CourseEventOutboxRepository outbox;
    private final int batchSize;

    public CourseMembershipBootstrapper(
            CourseEventOutboxRepository outbox,
            @org.springframework.beans.factory.annotation.Value("${onlinejudge.reliability.course-bootstrap.batch-size:100}") int batchSize
    ) {
        this.outbox = outbox;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${onlinejudge.reliability.course-bootstrap.fixed-delay-ms:30000}")
    public void reconcilePreexistingCourses() {
        bootstrapMissingRosters();
        reconcilePublishedRosters();
    }

    /** Returns the number of source Course aggregates newly checkpointed. */
    public int bootstrapMissingRosters() {
        int bootstrapped = 0;
        for (Long courseId : outbox.coursesMissingBootstrapRosters(batchSize)) {
            if (outbox.appendBootstrapRosterIfAbsent(courseId)) {
                bootstrapped += 1;
            }
        }
        return bootstrapped;
    }

    /**
     * Source-owned periodic recovery for a downstream projection restore.
     * Course does not inspect Learning state or wait for a synchronous
     * callback: its durable checkpoint makes one re-snapshot due at a time,
     * and the new outbox row advances the canonical course roster version.
     */
    public int reconcilePublishedRosters() {
        return reconcilePublishedRosters(Instant.now());
    }

    /** Visible for deterministic acceptance tests of the durable due gate. */
    public int reconcilePublishedRosters(Instant now) {
        int reconciled = 0;
        for (Long courseId : outbox.coursesDueForRosterReconciliation(now, batchSize)) {
            if (outbox.appendReconciliationRosterIfDue(courseId, now)) {
                reconciled += 1;
            }
        }
        return reconciled;
    }
}
