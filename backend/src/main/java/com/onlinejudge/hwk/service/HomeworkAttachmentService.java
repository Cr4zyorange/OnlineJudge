package com.onlinejudge.hwk.service;

import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachment;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentDownload;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentView;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class HomeworkAttachmentService {
    private static final Map<String, FileRule> FILE_RULES = Map.ofEntries(
            Map.entry("pdf", new FileRule("application/pdf", Set.of("application/pdf"), Signature.PDF)),
            Map.entry("png", new FileRule("image/png", Set.of("image/png"), Signature.PNG)),
            Map.entry("jpg", new FileRule("image/jpeg", Set.of("image/jpeg", "image/jpg"), Signature.JPEG)),
            Map.entry("jpeg", new FileRule("image/jpeg", Set.of("image/jpeg", "image/jpg"), Signature.JPEG)),
            Map.entry("zip", new FileRule("application/zip", Set.of("application/zip", "application/x-zip-compressed"), Signature.ZIP)),
            Map.entry("docx", new FileRule("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), Signature.ZIP)),
            Map.entry("xlsx", new FileRule("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), Signature.ZIP)),
            Map.entry("pptx", new FileRule("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"), Signature.ZIP)),
            Map.entry("txt", new FileRule("text/plain", Set.of("text/plain"), Signature.TEXT)),
            Map.entry("md", new FileRule("text/markdown", Set.of("text/markdown", "text/plain"), Signature.TEXT)),
            Map.entry("csv", new FileRule("text/csv", Set.of("text/csv", "text/plain", "application/vnd.ms-excel"), Signature.TEXT))
    );

    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkSubmissionAttachmentRepository attachmentRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final FileStorageService fileStorageService;
    private final long maxSizeBytes;
    private final Duration uploadTtl;
    private final int zipMaxEntries;

    public HomeworkAttachmentService(
            HomeworkRepository homeworkRepository,
            HomeworkSubmissionRepository submissionRepository,
            HomeworkSubmissionAttachmentRepository attachmentRepository,
            CoursePermissionClient coursePermissionClient,
            FileStorageService fileStorageService,
            @Value("${onlinejudge.hwk.attachments.max-size-bytes:10485760}") long maxSizeBytes,
            @Value("${onlinejudge.hwk.attachments.upload-ttl:PT24H}") Duration uploadTtl,
            @Value("${onlinejudge.hwk.attachments.zip-max-entries:128}") int zipMaxEntries
    ) {
        this.homeworkRepository = homeworkRepository;
        this.submissionRepository = submissionRepository;
        this.attachmentRepository = attachmentRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.fileStorageService = fileStorageService;
        this.maxSizeBytes = maxSizeBytes;
        this.uploadTtl = uploadTtl;
        this.zipMaxEntries = Math.max(1, zipMaxEntries);
    }

    @Transactional
    public HomeworkSubmissionAttachment upload(long homeworkId, long uploaderId, MultipartFile file) {
        Homework homework = findHomework(homeworkId);
        requireCanUpload(homework, uploaderId);
        ValidatedUpload upload = validateUpload(file);
        HomeworkSubmissionAttachment previousUpload = attachmentRepository
                .findActiveUploadedForUpdate(homework.id(), uploaderId)
                .orElse(null);

        StoredFile storedFile;
        try {
            storedFile = fileStorageService.store(
                    upload.filename(),
                    upload.contentType(),
                    new ByteArrayInputStream(upload.bytes())
            );
        } catch (RuntimeException exception) {
            throw storageFailure();
        }
        if (storedFile == null || storedFile.storageKey() == null || storedFile.storageKey().isBlank()
                || storedFile.size() != upload.bytes().length) {
            safeDelete(storedFile == null ? null : storedFile.storageKey());
            throw storageFailure();
        }
        registerRollbackCleanup(storedFile.storageKey());

        LocalDateTime now = LocalDateTime.now();
        if (previousUpload != null) {
            if (!attachmentRepository.markDeleted(previousUpload.id(), now)) {
                throw new HomeworkApiException("HWK_4092", "attachment state changed", HttpStatus.CONFLICT);
            }
            registerAfterCommitDelete(previousUpload.id(), previousUpload.storageKey());
        }
        HomeworkSubmissionAttachment attachment = new HomeworkSubmissionAttachment(
                0L,
                UUID.randomUUID().toString(),
                null,
                homework.id(),
                homework.courseId(),
                uploaderId,
                storedFile.storageKey(),
                upload.filename(),
                upload.contentType(),
                storedFile.size(),
                HomeworkSubmissionAttachmentStatus.UPLOADED,
                1,
                now.plus(uploadTtl),
                null,
                now,
                now,
                null
        );
        try {
            return attachmentRepository.save(attachment);
        } catch (DuplicateKeyException | ConcurrencyFailureException exception) {
            throw new HomeworkApiException("HWK_4092", "active attachment already exists", HttpStatus.CONFLICT);
        } catch (DataIntegrityViolationException exception) {
            throw storageFailure();
        }
    }

    @Transactional(readOnly = true)
    public HomeworkSubmissionAttachment getUnbound(long homeworkId, String publicId, long uploaderId) {
        String canonicalPublicId = canonicalPublicId(publicId);
        Homework homework = findHomework(homeworkId);
        requireCourseView(homework, uploaderId);
        HomeworkSubmissionAttachment attachment = attachmentRepository.findByPublicId(canonicalPublicId)
                .orElseThrow(this::hiddenAttachment);
        requireOwnedUpload(attachment, homework, uploaderId, false);
        loadVerified(attachment);
        return attachment;
    }

    @Transactional
    public void deleteUnbound(long homeworkId, String publicId, long uploaderId) {
        String canonicalPublicId = canonicalPublicId(publicId);
        Homework homework = findHomework(homeworkId);
        requireCourseView(homework, uploaderId);
        HomeworkSubmissionAttachment attachment = attachmentRepository.findByPublicIdForUpdate(canonicalPublicId)
                .orElseThrow(this::hiddenAttachment);
        requireOwnedUpload(attachment, homework, uploaderId, true);
        if (!attachmentRepository.markDeleted(attachment.id(), LocalDateTime.now())) {
            throw new HomeworkApiException("HWK_4092", "attachment state changed", HttpStatus.CONFLICT);
        }
        registerAfterCommitDelete(attachment.id(), attachment.storageKey());
    }

    public HomeworkSubmissionAttachment lockBindable(
            String publicId,
            Homework homework,
            long studentId,
            LocalDateTime now
    ) {
        String canonicalPublicId = canonicalPublicId(publicId);
        HomeworkSubmissionAttachment attachment = attachmentRepository.findByPublicIdForUpdate(canonicalPublicId)
                .orElseThrow(this::hiddenAttachment);
        if (attachment.homeworkId() != homework.id()
                || attachment.courseId() != homework.courseId()
                || attachment.uploaderId() != studentId) {
            throw hiddenAttachment();
        }
        if (attachment.status() == HomeworkSubmissionAttachmentStatus.BOUND) {
            throw new HomeworkApiException("HWK_4092", "attachment is already bound", HttpStatus.CONFLICT);
        }
        if (attachment.status() != HomeworkSubmissionAttachmentStatus.UPLOADED
                || attachment.submissionId() != null
                || attachment.expiresAt() == null
                || !attachment.expiresAt().isAfter(now)) {
            throw new HomeworkApiException("HWK_4091", "attachment is unavailable", HttpStatus.CONFLICT);
        }
        requireTrustedMetadata(attachment);
        loadVerified(attachment);
        return attachment;
    }

    public void bind(HomeworkSubmissionAttachment attachment, long submissionId, LocalDateTime now) {
        if (!attachmentRepository.bind(attachment.id(), submissionId, now)) {
            throw new HomeworkApiException("HWK_4092", "attachment binding conflict", HttpStatus.CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public HomeworkSubmissionAttachmentView viewForSubmission(HomeworkSubmission submission, long courseId) {
        return attachmentRepository.findBySubmissionId(submission.id())
                .filter(attachment -> attachment.status() == HomeworkSubmissionAttachmentStatus.BOUND)
                .filter(attachment -> validBinding(attachment, submission, courseId))
                .filter(this::trustedMetadata)
                .map(attachment -> new HomeworkSubmissionAttachmentView(
                        attachment.originalFilename(),
                        attachment.contentType(),
                        attachment.fileSize(),
                        true
                ))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public HomeworkSubmissionAttachmentDownload download(
            long homeworkId,
            long submissionId,
            long userId
    ) {
        Homework homework = findDownloadHomework(homeworkId);
        boolean courseManager = coursePermissionClient.canManageCourse(homework.courseId(), userId);
        if (!courseManager) {
            requireCourseView(homework, userId);
        }
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.homeworkId() == homeworkId)
                .orElseThrow(this::hiddenAttachment);

        if (!courseManager && submission.studentId() != userId) {
            throw new HomeworkApiException("HWK_4031", "attachment download denied", HttpStatus.FORBIDDEN);
        }

        HomeworkSubmissionAttachment attachment = attachmentRepository.findBySubmissionId(submissionId)
                .orElseThrow(this::hiddenAttachment);
        if (attachment.status() != HomeworkSubmissionAttachmentStatus.BOUND
                || !validBinding(attachment, submission, homework.courseId())) {
            throw hiddenAttachment();
        }
        requireTrustedMetadata(attachment);
        StoredFile storedFile = loadVerified(attachment);
        return new HomeworkSubmissionAttachmentDownload(
                attachment.originalFilename(),
                attachment.contentType(),
                attachment.fileSize(),
                storedFile.resource()
        );
    }

    private Homework findHomework(long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(homework -> !homework.deleted())
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "homework not found", HttpStatus.NOT_FOUND));
    }

    private Homework findDownloadHomework(long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(homework -> !homework.deleted())
                .orElseThrow(this::hiddenAttachment);
    }

    private void requireCanUpload(Homework homework, long uploaderId) {
        requireCourseView(homework, uploaderId);
        if (homework.type() != HomeworkType.FILE) {
            throw new HomeworkApiException("HWK_4005", "homework does not accept attachments", HttpStatus.BAD_REQUEST);
        }
        if (homework.status() != HomeworkStatus.PUBLISHED) {
            throw new HomeworkApiException("HWK_4002", "homework is not published", HttpStatus.CONFLICT);
        }
        if (LocalDateTime.now().isAfter(homework.deadline()) && !homework.allowLateSubmit()) {
            throw new HomeworkApiException("HWK_4004", "deadline exceeded", HttpStatus.CONFLICT);
        }
    }

    private void requireCourseView(Homework homework, long userId) {
        if (!coursePermissionClient.canViewCourse(homework.courseId(), userId)) {
            throw new HomeworkApiException("HWK_4031", "course access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireOwnedUpload(
            HomeworkSubmissionAttachment attachment,
            Homework homework,
            long uploaderId,
            boolean deleting
    ) {
        if (attachment.homeworkId() != homework.id()
                || attachment.courseId() != homework.courseId()
                || attachment.uploaderId() != uploaderId
                || attachment.status() == HomeworkSubmissionAttachmentStatus.DELETED) {
            throw hiddenAttachment();
        }
        if (attachment.status() == HomeworkSubmissionAttachmentStatus.BOUND || attachment.submissionId() != null) {
            if (deleting) {
                throw new HomeworkApiException("HWK_4092", "bound attachment cannot be removed", HttpStatus.CONFLICT);
            }
            throw hiddenAttachment();
        }
        if (attachment.expiresAt() == null || !attachment.expiresAt().isAfter(LocalDateTime.now())) {
            throw new HomeworkApiException("HWK_4091", "attachment is unavailable", HttpStatus.CONFLICT);
        }
        requireTrustedMetadata(attachment);
    }

    private ValidatedUpload validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw invalidFile("attachment must not be empty");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new HomeworkApiException("HWK_4131", "attachment exceeds 10 MiB", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        FileRule rule = FILE_RULES.get(extension);
        if (rule == null) {
            throw unsupportedFile("attachment type is not supported");
        }
        String reportedType = normalizeContentType(file.getContentType());
        if (!rule.acceptedContentTypes().contains(reportedType)) {
            throw unsupportedFile("attachment content type does not match its filename");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception exception) {
            throw storageFailure();
        }
        if (bytes.length == 0 || bytes.length > maxSizeBytes || !matchesSignature(bytes, rule.signature())) {
            throw invalidFile("attachment content signature is invalid");
        }
        if (rule.signature() == Signature.ZIP && !validArchive(bytes, extension)) {
            throw invalidFile("attachment archive structure is invalid");
        }
        return new ValidatedUpload(filename, rule.canonicalContentType(), bytes);
    }

    private boolean validArchive(byte[] bytes, String extension) {
        Path temporaryArchive = null;
        try {
            temporaryArchive = Files.createTempFile("hwk-upload-validation-", ".zip");
            Files.write(temporaryArchive, bytes);
            try (ZipFile zipFile = new ZipFile(temporaryArchive.toFile(), StandardCharsets.UTF_8)) {
                if (zipFile.size() == 0 || zipFile.size() > zipMaxEntries) {
                    return false;
                }
                Set<String> entryNames = new HashSet<>();
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                int entryCount = 0;
                while (entries.hasMoreElements()) {
                    String entryName = entries.nextElement().getName();
                    entryCount++;
                    if (entryCount > zipMaxEntries
                            || !safeArchiveEntryName(entryName)
                            || !entryNames.add(entryName)) {
                        return false;
                    }
                }
                if ("zip".equals(extension)) {
                    return true;
                }
                String requiredDirectory = switch (extension) {
                    case "docx" -> "word/";
                    case "xlsx" -> "xl/";
                    case "pptx" -> "ppt/";
                    default -> null;
                };
                return requiredDirectory != null
                        && entryNames.contains("[Content_Types].xml")
                        && entryNames.stream().anyMatch(name -> name.startsWith(requiredDirectory)
                                && !name.equals(requiredDirectory)
                                && !name.endsWith("/"));
            }
        } catch (Exception exception) {
            return false;
        } finally {
            if (temporaryArchive != null) {
                try {
                    Files.deleteIfExists(temporaryArchive);
                } catch (Exception ignored) {
                    // The operating system can reclaim a validation-only temporary file.
                }
            }
        }
    }

    private boolean safeArchiveEntryName(String entryName) {
        if (entryName == null
                || entryName.isBlank()
                || entryName.length() > 1024
                || entryName.startsWith("/")
                || entryName.startsWith("\\")
                || entryName.indexOf('\\') >= 0
                || entryName.indexOf('\0') >= 0
                || (entryName.length() > 1 && entryName.charAt(1) == ':')) {
            return false;
        }
        for (String segment : entryName.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private StoredFile loadVerified(HomeworkSubmissionAttachment attachment) {
        StoredFile storedFile;
        try {
            storedFile = fileStorageService.load(attachment.storageKey());
            if (storedFile == null
                    || storedFile.resource() == null
                    || !storedFile.resource().exists()
                    || !storedFile.resource().isReadable()
                    || storedFile.size() != attachment.fileSize()
                    || storedFile.resource().contentLength() != attachment.fileSize()) {
                throw new IllegalStateException("attachment storage metadata mismatch");
            }
        } catch (Exception exception) {
            throw storageFailure();
        }
        return storedFile;
    }

    private void requireTrustedMetadata(HomeworkSubmissionAttachment attachment) {
        if (!trustedMetadata(attachment)) {
            throw new HomeworkApiException("HWK_4091", "attachment is unavailable", HttpStatus.CONFLICT);
        }
    }

    private boolean trustedMetadata(HomeworkSubmissionAttachment attachment) {
        if (!safeStoredFilename(attachment.originalFilename())
                || attachment.fileSize() <= 0
                || attachment.fileSize() > maxSizeBytes
                || attachment.storageKey() == null
                || attachment.storageKey().isBlank()
                || attachment.contentType() == null
                || attachment.contentType().length() > 128
                || attachment.contentType().indexOf('\r') >= 0
                || attachment.contentType().indexOf('\n') >= 0) {
            return false;
        }
        FileRule rule = FILE_RULES.get(extension(attachment.originalFilename()));
        return rule != null && rule.canonicalContentType().equals(attachment.contentType());
    }

    private boolean safeStoredFilename(String filename) {
        if (filename == null
                || filename.isBlank()
                || filename.length() > 255
                || !filename.equals(filename.trim())) {
            return false;
        }
        for (int index = 0; index < filename.length(); index++) {
            char current = filename.charAt(index);
            if (current == '/' || current == '\\' || Character.isISOControl(current)) {
                return false;
            }
        }
        return true;
    }

    private boolean validBinding(
            HomeworkSubmissionAttachment attachment,
            HomeworkSubmission submission,
            long courseId
    ) {
        return attachment.submissionId() != null
                && attachment.submissionId() == submission.id()
                && attachment.homeworkId() == submission.homeworkId()
                && attachment.courseId() == courseId
                && attachment.uploaderId() == submission.studentId()
                && attachment.boundAt() != null
                && attachment.deletedAt() == null;
    }

    private String sanitizeFilename(String filename) {
        String value = filename == null ? "" : filename.trim();
        if (value.isEmpty()) {
            throw invalidFile("attachment filename is required");
        }
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), 255));
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            sanitized.append(current == '/' || current == '\\' || Character.isISOControl(current) ? '_' : current);
        }
        if (sanitized.length() <= 255) {
            return sanitized.toString();
        }
        String extension = extension(sanitized.toString());
        String suffix = extension.isEmpty() ? "" : "." + extension;
        return sanitized.substring(0, 255 - suffix.length()) + suffix;
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index <= 0 || index == filename.length() - 1
                ? ""
                : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        return parameters < 0 ? normalized : normalized.substring(0, parameters).trim();
    }

    private boolean matchesSignature(byte[] bytes, Signature signature) {
        return switch (signature) {
            case PDF -> startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case PNG -> startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case JPEG -> startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case ZIP -> startsWith(bytes, new byte[]{'P', 'K', 0x03, 0x04})
                    || startsWith(bytes, new byte[]{'P', 'K', 0x05, 0x06})
                    || startsWith(bytes, new byte[]{'P', 'K', 0x07, 0x08});
            case TEXT -> isUtf8Text(bytes);
        };
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    safeDelete(storageKey);
                }
            }
        });
    }

    private void registerAfterCommitDelete(long attachmentId, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteAndPurgeTombstone(attachmentId, storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteAndPurgeTombstone(attachmentId, storageKey);
            }
        });
    }

    private void deleteAndPurgeTombstone(long attachmentId, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            fileStorageService.delete(storageKey);
            attachmentRepository.purgeDeleted(attachmentId);
        } catch (RuntimeException ignored) {
            // Keep the tombstone so scheduled cleanup can retry storage deletion or purging.
        }
    }

    private void safeDelete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException deleteFailure) {
            try {
                fileStorageService.deferDelete(storageKey);
            } catch (RuntimeException journalFailure) {
                journalFailure.addSuppressed(deleteFailure);
                throw journalFailure;
            }
        }
    }

    private HomeworkApiException hiddenAttachment() {
        return new HomeworkApiException("HWK_4042", null, HttpStatus.NOT_FOUND);
    }

    private HomeworkApiException invalidFile(String message) {
        return new HomeworkApiException("HWK_4005", message, HttpStatus.BAD_REQUEST);
    }

    private HomeworkApiException unsupportedFile(String message) {
        return new HomeworkApiException("HWK_4151", message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private String canonicalPublicId(String publicId) {
        if (publicId == null || publicId.length() != 36) {
            throw hiddenAttachment();
        }
        try {
            String canonical = UUID.fromString(publicId).toString();
            if (!canonical.equals(publicId.toLowerCase(Locale.ROOT))) {
                throw hiddenAttachment();
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw hiddenAttachment();
        }
    }

    private HomeworkApiException storageFailure() {
        return new HomeworkApiException("HWK_5002", "attachment storage failure", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private record ValidatedUpload(String filename, String contentType, byte[] bytes) {
    }

    private record FileRule(String canonicalContentType, Set<String> acceptedContentTypes, Signature signature) {
    }

    private enum Signature {
        PDF,
        PNG,
        JPEG,
        ZIP,
        TEXT
    }
}
