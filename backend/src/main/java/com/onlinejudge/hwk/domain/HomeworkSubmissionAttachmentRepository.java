package com.onlinejudge.hwk.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HomeworkSubmissionAttachmentRepository {
    HomeworkSubmissionAttachment save(HomeworkSubmissionAttachment attachment);

    Optional<HomeworkSubmissionAttachment> findByPublicId(String publicId);

    Optional<HomeworkSubmissionAttachment> findByPublicIdForUpdate(String publicId);

    Optional<HomeworkSubmissionAttachment> findActiveUploadedForUpdate(long homeworkId, long uploaderId);

    Optional<HomeworkSubmissionAttachment> findBySubmissionId(long submissionId);

    List<HomeworkSubmissionAttachment> findExpiredUploadedForUpdate(LocalDateTime now, int limit);

    List<HomeworkSubmissionAttachment> findDeletedForUpdate(int limit);

    boolean bind(long id, long submissionId, LocalDateTime boundAt);

    boolean markDeleted(long id, LocalDateTime deletedAt);

    boolean purgeDeleted(long id);
}
