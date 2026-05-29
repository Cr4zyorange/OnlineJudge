package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.CreateLabSubmissionCommand;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class LabSubmissionService {
    private static final long MAX_UPLOAD_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> GENERIC_SOURCE_CONTENT_TYPES = Set.of(
            "application/octet-stream",
            "text/plain"
    );
    private static final Map<String, Set<String>> SOURCE_FILE_EXTENSIONS = Map.of(
            "java", Set.of("java"),
            "python", Set.of("py"),
            "cpp", Set.of("cpp", "cc", "cxx"),
            "c", Set.of("c")
    );
    private static final Map<String, Set<String>> SOURCE_FILE_CONTENT_TYPES = Map.of(
            "java", Set.of("text/x-java-source", "text/java", "application/java", "application/x-java"),
            "python", Set.of("text/x-python", "application/x-python-code"),
            "cpp", Set.of("text/x-c++src", "text/x-c++source", "application/x-c++src"),
            "c", Set.of("text/x-csrc", "text/x-c")
    );

    private final LabExperimentRepository labExperimentRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final FileStorageService fileStorageService;

    public LabSubmissionService(
            LabExperimentRepository labExperimentRepository,
            LabSubmissionRepository labSubmissionRepository,
            CoursePermissionClient coursePermissionClient,
            FileStorageService fileStorageService
    ) {
        this.labExperimentRepository = labExperimentRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public LabSubmission submit(long labId, long studentId, CreateLabSubmissionCommand command) {
        LabExperiment experiment = labExperimentRepository.findById(labId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("实验不存在"));

        requireStudentCanSubmit(experiment, studentId);

        String normalizedLanguage = normalizeLanguage(command.language());
        validateSubmissionContent(command, normalizedLanguage, experiment.allowedLanguages());

        LocalDateTime now = LocalDateTime.now();
        Optional<LabSubmission> latestFinalSubmission = labSubmissionRepository.findLatestFinalByLabIdAndStudentId(labId, studentId);
        latestFinalSubmission.ifPresent(existing -> labSubmissionRepository.update(existing.markHistorical(now)));

        StoredFile storedFile = null;
        if (hasFile(command)) {
            storedFile = fileStorageService.store(
                    command.fileName(),
                    command.fileContentType(),
                    new ByteArrayInputStream(command.fileBytes())
            );
        }

        EvaluationStatus evaluationStatus = experiment.autoEvaluate() ? EvaluationStatus.PENDING : EvaluationStatus.NONE;
        LabSubmission submission = new LabSubmission(
                0L,
                labId,
                studentId,
                normalizeCode(command.code()),
                storedFile == null ? null : storedFile.storageKey(),
                normalizedLanguage,
                LabSubmitStatus.SUBMITTED,
                evaluationStatus,
                null,
                null,
                latestFinalSubmission.map(item -> item.version() + 1).orElse(1),
                true,
                now,
                now,
                now,
                false
        );

        return labSubmissionRepository.save(submission);
    }

    private void requireStudentCanSubmit(LabExperiment experiment, long studentId) {
        if (!coursePermissionClient.canViewCourse(experiment.courseId(), studentId)) {
            throw new LabPermissionException("无课程访问权限");
        }
        if (experiment.status() != LabExperimentStatus.PUBLISHED) {
            throw new LabStateException("当前实验状态不允许提交");
        }
        if (!experiment.deadline().isAfter(LocalDateTime.now())) {
            throw new LabStateException("实验已截止，当前不允许提交");
        }
    }

    private void validateSubmissionContent(
            CreateLabSubmissionCommand command,
            String normalizedLanguage,
            String allowedLanguages
    ) {
        if (!hasCode(command) && !hasFile(command)) {
            throw new LabSubmissionValidationException("LAB-400-03", "提交代码不能为空且必须上传文件");
        }
        if (allowedLanguages != null && !allowedLanguages.isBlank()) {
            boolean supported = Arrays.stream(allowedLanguages.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(value -> value.equals(normalizedLanguage));
            if (!supported) {
                throw new LabSubmissionValidationException("LAB-400-04", "编程语言不在实验允许范围内");
            }
        }
        if (hasFile(command)) {
            validateSourceFile(command, normalizedLanguage);
            if (command.fileBytes().length > MAX_UPLOAD_SIZE_BYTES) {
                throw new LabSubmissionValidationException("LAB-400-06", "提交文件大小超过系统限制");
            }
        }
    }

    private boolean hasCode(CreateLabSubmissionCommand command) {
        return normalizeCode(command.code()) != null;
    }

    private boolean hasFile(CreateLabSubmissionCommand command) {
        return command.fileBytes() != null && command.fileBytes().length > 0;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            throw new LabSubmissionValidationException("LAB-400-04", "编程语言不在实验允许范围内");
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    private void validateSourceFile(CreateLabSubmissionCommand command, String normalizedLanguage) {
        String fileName = command.fileName() == null ? "" : command.fileName().trim();
        String extension = extractExtension(fileName);
        Set<String> allowedExtensions = SOURCE_FILE_EXTENSIONS.get(normalizedLanguage);
        if (allowedExtensions == null || !allowedExtensions.contains(extension)) {
            throw new LabSubmissionValidationException(
                    "LAB-400-06",
                    "提交文件格式不合法，仅支持 " + formatExtensions(allowedExtensions) + " 源码文件"
            );
        }

        String contentType = normalizeContentType(command.fileContentType());
        if (contentType != null && !isSupportedContentType(normalizedLanguage, contentType)) {
            throw new LabSubmissionValidationException(
                    "LAB-400-06",
                    "提交文件类型不合法，仅支持 " + formatExtensions(allowedExtensions) + " 源码文件"
            );
        }
    }

    private String extractExtension(String fileName) {
        int separatorIndex = fileName.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedContentType(String normalizedLanguage, String contentType) {
        if (GENERIC_SOURCE_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        return SOURCE_FILE_CONTENT_TYPES.getOrDefault(normalizedLanguage, Set.of()).contains(contentType);
    }

    private String formatExtensions(Set<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return "受支持";
        }
        TreeSet<String> sortedExtensions = new TreeSet<>(extensions);
        return sortedExtensions.stream()
                .map(extension -> "." + extension)
                .reduce((left, right) -> left + "、" + right)
                .orElse("受支持");
    }
}
