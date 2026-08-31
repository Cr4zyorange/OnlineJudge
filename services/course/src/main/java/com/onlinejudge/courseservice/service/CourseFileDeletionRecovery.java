package com.onlinejudge.courseservice.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recovery sweep for the transaction-aware resource delete journal.  A crash
 * between the commit that hides the resource row and the physical file removal
 * leaves a PENDING entry; this sweep retries it until the object is gone.
 */
@Component
public class CourseFileDeletionRecovery {
    private final CourseService service;

    public CourseFileDeletionRecovery(CourseService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${COURSE_FILE_DELETE_RECOVERY_INTERVAL:PT1M}")
    public void recoverPendingDeletions() {
        service.recoverPendingFileDeletions();
    }
}
