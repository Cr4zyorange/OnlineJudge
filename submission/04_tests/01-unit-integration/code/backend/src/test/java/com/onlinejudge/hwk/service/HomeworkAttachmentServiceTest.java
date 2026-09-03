package com.onlinejudge.hwk.service;

import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeworkAttachmentServiceTest {
    private static final byte[] VALID_PDF = (
            "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"
    ).getBytes(StandardCharsets.US_ASCII);

    @Test
    void concurrentFirstInsertLockFailureReturnsStableAttachmentConflict() {
        HomeworkRepository homeworkRepository = mock(HomeworkRepository.class);
        HomeworkSubmissionAttachmentRepository attachmentRepository =
                mock(HomeworkSubmissionAttachmentRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(homeworkRepository.findById(11L)).thenReturn(Optional.of(publishedFileHomework()));
        when(attachmentRepository.findActiveUploadedForUpdate(11L, 601L)).thenReturn(Optional.empty());
        when(storage.store(anyString(), anyString(), any(InputStream.class))).thenReturn(new StoredFile(
                "internal-object-key",
                "answer.pdf",
                "application/pdf",
                VALID_PDF.length,
                "file:///must-not-leak"
        ));
        when(attachmentRepository.save(any()))
                .thenThrow(new CannotAcquireLockException("simulated MySQL 1213 deadlock"));
        HomeworkAttachmentService service = new HomeworkAttachmentService(
                homeworkRepository,
                mock(HomeworkSubmissionRepository.class),
                attachmentRepository,
                (courseId, userId) -> true,
                storage,
                10 * 1024 * 1024,
                Duration.ofHours(24),
                128
        );

        assertThatThrownBy(() -> service.upload(
                11L,
                601L,
                new MockMultipartFile("file", "answer.pdf", "application/pdf", VALID_PDF)
        ))
                .isInstanceOf(HomeworkApiException.class)
                .extracting("code")
                .isEqualTo("HWK_4092");
    }

    private Homework publishedFileHomework() {
        LocalDateTime now = LocalDateTime.now();
        return new Homework(
                11L,
                101L,
                null,
                "FILE homework",
                "Upload one attachment",
                HomeworkType.FILE,
                HomeworkStatus.PUBLISHED,
                100,
                now.plusDays(1),
                true,
                false,
                false,
                null,
                501L,
                now,
                false,
                now,
                now,
                List.of(),
                List.of(),
                null
        );
    }
}
