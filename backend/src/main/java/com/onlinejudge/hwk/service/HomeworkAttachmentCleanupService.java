package com.onlinejudge.hwk.service;

import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachment;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(
        prefix = "onlinejudge.hwk.attachments",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HomeworkAttachmentCleanupService {
    private final HomeworkSubmissionAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public HomeworkAttachmentCleanupService(
            HomeworkSubmissionAttachmentRepository attachmentRepository,
            FileStorageService fileStorageService,
            TransactionTemplate transactionTemplate,
            @Value("${onlinejudge.hwk.attachments.cleanup-batch-size:100}") int batchSize
    ) {
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${onlinejudge.hwk.attachments.cleanup-delay-ms:3600000}")
    public void cleanupExpiredUploads() {
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> markExpiredUploads(now));
        transactionTemplate.executeWithoutResult(status -> retryDeletedPhysicalFiles());
        retryDeferredPhysicalFiles();
    }

    private void markExpiredUploads(LocalDateTime now) {
        List<HomeworkSubmissionAttachment> expired = attachmentRepository.findExpiredUploadedForUpdate(now, batchSize);
        for (HomeworkSubmissionAttachment attachment : expired) {
            attachmentRepository.markDeleted(attachment.id(), now);
        }
    }

    private void retryDeletedPhysicalFiles() {
        for (HomeworkSubmissionAttachment attachment : attachmentRepository.findDeletedForUpdate(batchSize)) {
            try {
                fileStorageService.delete(attachment.storageKey());
                attachmentRepository.purgeDeleted(attachment.id());
            } catch (RuntimeException ignored) {
                // Keep the tombstone so the next scheduled pass can retry.
            }
        }
    }

    private void retryDeferredPhysicalFiles() {
        for (String storageKey : fileStorageService.pendingDeletes(batchSize)) {
            try {
                fileStorageService.delete(storageKey);
                fileStorageService.completeDeferredDelete(storageKey);
            } catch (RuntimeException ignored) {
                // Keep the durable marker so the next scheduled pass can retry.
            }
        }
    }
}
