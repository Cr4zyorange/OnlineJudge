package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.LabEvaluation;
import com.onlinejudge.lab.domain.LabEvaluationCaseResult;
import com.onlinejudge.lab.domain.LabEvaluationRepository;
import com.onlinejudge.lab.domain.LabEvaluationResultRepository;
import com.onlinejudge.lab.domain.LabEvaluationResultView;
import com.onlinejudge.lab.domain.CreateLabSubmissionCommand;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabReportSummaryView;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabResultView;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionDetailView;
import com.onlinejudge.lab.domain.LabSubmissionHistoryItemView;
import com.onlinejudge.lab.domain.LabSubmissionQuery;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabSubmissionSourceDownload;
import com.onlinejudge.lab.domain.LabSubmissionSourceFile;
import com.onlinejudge.lab.domain.LabSubmissionSourceFileRepository;
import com.onlinejudge.lab.domain.LabSubmissionSourceFileStatus;
import com.onlinejudge.lab.domain.LabSubmissionSourceFileView;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class LabSubmissionService {
    private static final long MAX_UPLOAD_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Comparator<LabSubmission> SUBMISSION_RECENCY = Comparator
            .comparing(LabSubmission::submittedAt)
            .thenComparingInt(LabSubmission::version)
            .thenComparingLong(LabSubmission::id);
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
            "python", Set.of("text/x-python", "text/x-python-script", "application/x-python-code"),
            "cpp", Set.of("text/x-c++src", "text/x-c++source", "application/x-c++src"),
            "c", Set.of("text/x-csrc", "text/x-c")
    );

    private final LabExperimentRepository labExperimentRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabSubmissionSourceFileRepository labSubmissionSourceFileRepository;
    private final LabEvaluationRepository labEvaluationRepository;
    private final LabEvaluationResultRepository labEvaluationResultRepository;
    private final LabEvaluationService labEvaluationService;
    private final LabReportService labReportService;
    private final LabScoreService labScoreService;
    private final CoursePermissionClient coursePermissionClient;
    private final FileStorageService fileStorageService;

    public LabSubmissionService(
            LabExperimentRepository labExperimentRepository,
            LabSubmissionRepository labSubmissionRepository,
            LabSubmissionSourceFileRepository labSubmissionSourceFileRepository,
            LabEvaluationRepository labEvaluationRepository,
            LabEvaluationResultRepository labEvaluationResultRepository,
            LabEvaluationService labEvaluationService,
            LabReportService labReportService,
            LabScoreService labScoreService,
            CoursePermissionClient coursePermissionClient,
            FileStorageService fileStorageService
    ) {
        this.labExperimentRepository = labExperimentRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labSubmissionSourceFileRepository = labSubmissionSourceFileRepository;
        this.labEvaluationRepository = labEvaluationRepository;
        this.labEvaluationService = labEvaluationService;
        this.labReportService = labReportService;
        this.labScoreService = labScoreService;
        this.coursePermissionClient = coursePermissionClient;
        this.fileStorageService = fileStorageService;
        this.labEvaluationResultRepository = labEvaluationResultRepository;
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
                    trustedSourceContentType(command.fileContentType()),
                    new ByteArrayInputStream(command.fileBytes())
            );
            registerFileRollbackCleanup(storedFile.storageKey());
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

        LabSubmission savedSubmission = labSubmissionRepository.save(submission);
        if (storedFile != null) {
            labSubmissionSourceFileRepository.save(new LabSubmissionSourceFile(
                    0L,
                    savedSubmission.id(),
                    savedSubmission.labId(),
                    experiment.courseId(),
                    savedSubmission.studentId(),
                    storedFile.storageKey(),
                    storedFile.originalFilename(),
                    storedFile.contentType(),
                    storedFile.size(),
                    LabSubmissionSourceFileStatus.AVAILABLE,
                    now,
                    now,
                    null
            ));
        }
        if (!experiment.autoEvaluate()) {
            return savedSubmission;
        }
        if (experiment.testcases().isEmpty()) {
            finalizeEmptyAutoEvaluation(savedSubmission, now);
            return savedSubmission;
        }
        markSubmissionPendingEvaluation(savedSubmission, experiment.testcases().size(), now);
        scheduleEvaluationAfterCommit(experiment, savedSubmission, resolveSubmissionSource(savedSubmission));
        return savedSubmission;
    }

    public List<LabSubmissionHistoryItemView> listSubmissions(long labId, long userId, LabSubmissionQuery query) {
        LabExperiment experiment = findExistingExperiment(labId);
        boolean canManage = coursePermissionClient.canManageCourse(experiment.courseId(), userId);
        List<LabSubmission> submissions = canManage
                ? labSubmissionRepository.findByLabId(labId)
                : findStudentSubmissions(experiment, userId, query);

        LabSubmissionQuery effectiveQuery = canManage ? query : sanitizeStudentQuery(query, userId);
        Map<Long, SubmissionVersionFlags> flagsBySubmissionId = buildSubmissionVersionFlags(submissions);
        return toHistoryItems(
                applyFilters(submissions, experiment, effectiveQuery),
                flagsBySubmissionId,
                canManage || areScoresPublished(experiment)
        );
    }

    public LabSubmissionDetailView getSubmissionDetail(long labId, long submissionId, long userId) {
        LabExperiment experiment = findExistingExperiment(labId);
        LabSubmission submission = labSubmissionRepository.findById(submissionId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("提交不存在"));

        if (submission.labId() != labId) {
            throw new LabNotFoundException("提交不存在");
        }

        boolean canManage = coursePermissionClient.canManageCourse(experiment.courseId(), userId);
        if (!canManage) {
            requireCourseViewPermission(experiment.courseId(), userId);
            if (submission.studentId() != userId) {
                throw new LabPermissionException("无权限查看他人提交");
            }
        }

        Map<Long, SubmissionVersionFlags> flagsBySubmissionId = buildSubmissionVersionFlags(
                labSubmissionRepository.findByLabIdAndStudentId(labId, submission.studentId())
        );
        LabSubmissionDetailView rawDetail = toDetail(
                submission,
                resolveSubmissionVersionFlags(flagsBySubmissionId, submission),
                experiment.courseId()
        );
        return toVisibleSubmissionDetail(rawDetail, canManage, areScoresPublished(experiment));
    }

    public LabSubmissionSourceDownload downloadSubmissionSource(long labId, long submissionId, long userId) {
        LabExperiment experiment = findExistingExperiment(labId);
        if (!coursePermissionClient.canManageCourse(experiment.courseId(), userId)) {
            throw new LabPermissionException("无课程管理权限");
        }

        LabSubmission submission = labSubmissionRepository.findById(submissionId)
                .filter(item -> !item.deleted())
                .filter(item -> item.labId() == labId)
                .orElseThrow(() -> new LabNotFoundException("提交不存在"));
        if (!hasFile(submission.fileId())) {
            throw LabSourceFileException.noFile();
        }

        LabSubmissionSourceFile sourceFile = labSubmissionSourceFileRepository.findBySubmissionId(submissionId)
                .orElseThrow(LabSourceFileException::unavailable);
        if (!hasValidSourceBinding(sourceFile, submission, experiment.courseId())) {
            throw new LabNotFoundException("提交不存在");
        }
        if (sourceFile.status() != LabSubmissionSourceFileStatus.AVAILABLE
                || !hasTrustedMetadata(sourceFile, submission.language())) {
            throw LabSourceFileException.unavailable();
        }

        StoredFile storedFile;
        try {
            storedFile = fileStorageService.load(sourceFile.storageKey());
        } catch (RuntimeException exception) {
            throw LabSourceFileException.storageFailure();
        }
        if (storedFile.resource() == null
                || !storedFile.resource().exists()
                || !storedFile.resource().isReadable()
                || storedFile.size() != sourceFile.fileSize()) {
            throw LabSourceFileException.storageFailure();
        }

        return new LabSubmissionSourceDownload(
                sourceFile.originalFilename(),
                sourceFile.contentType(),
                sourceFile.fileSize(),
                storedFile.resource()
        );
    }

    public LabEvaluationResultView getSubmissionResult(long labId, long submissionId, long userId) {
        SubmissionAccess access = verifySubmissionAccess(labId, submissionId, userId);
        LabSubmission submission = access.submission();
        List<LabEvaluationCaseResult> allCaseResults = labEvaluationResultRepository.findBySubmissionId(submissionId);
        List<LabEvaluationCaseResult> visibleCaseResults = allCaseResults;
        if (!access.canManage()) {
            visibleCaseResults = allCaseResults.stream()
                    .filter(LabEvaluationCaseResult::isPublic)
                    .map(LabEvaluationCaseResult::hideSensitiveContent)
                    .toList();
        }
        int passedCases = (int) allCaseResults.stream().filter(LabEvaluationCaseResult::passed).count();
        int totalCases = allCaseResults.size();
        String fallbackMessage = resolveEvaluationMessage(submission.evaluationStatus(), passedCases, totalCases);
        LabEvaluation evaluation = labEvaluationRepository.findLatestBySubmissionId(submissionId)
                .orElseGet(() -> new LabEvaluation(
                        0L,
                        submissionId,
                        submission.evaluationStatus(),
                        submission.autoScore() == null ? 0 : submission.autoScore(),
                        passedCases,
                        totalCases,
                        null,
                        null,
                        fallbackMessage,
                        null,
                        null,
                        submission.submittedAt(),
                        submission.updatedAt(),
                        submission.createdAt(),
                        submission.updatedAt()
                ));
        return new LabEvaluationResultView(
                submission.id(),
                evaluation.status(),
                evaluation.score(),
                evaluation.passedCases(),
                evaluation.totalCases(),
                evaluation.feedback(),
                visibleCaseResults,
                submission.submittedAt(),
                evaluation.finishedAt() == null ? submission.updatedAt() : evaluation.finishedAt()
        );
    }

    public LabResultView getLabResult(long labId, long studentId, long userId) {
        LabExperiment experiment = findExistingExperiment(labId);
        boolean canManage = coursePermissionClient.canManageCourse(experiment.courseId(), userId);
        requireCourseViewPermission(experiment.courseId(), userId);
        if (!canManage && studentId != userId) {
            throw new LabPermissionException("无权限查看他人成绩");
        }

        LabSubmission submission = labSubmissionRepository.findLatestFinalByLabIdAndStudentId(labId, studentId)
                .orElseThrow(() -> new LabNotFoundException("实验结果不存在"));
        Map<Long, SubmissionVersionFlags> flagsBySubmissionId = buildSubmissionVersionFlags(
                labSubmissionRepository.findByLabIdAndStudentId(labId, studentId)
        );
        LabSubmissionDetailView rawDetail = toDetail(
                submission,
                resolveSubmissionVersionFlags(flagsBySubmissionId, submission),
                experiment.courseId()
        );
        LabEvaluationResultView evaluationResult = getSubmissionResult(labId, submission.id(), canManage ? userId : studentId);
        boolean scorePublished = areScoresPublished(experiment);
        LabSubmissionDetailView visibleSubmission = toVisibleSubmissionDetail(rawDetail, canManage, scorePublished);
        LabReportSummaryView visibleReport = visibleSubmission.latestReport();

        return new LabResultView(
                labId,
                studentId,
                experiment.status(),
                visibleSubmission,
                evaluationResult,
                visibleReport,
                visibleSubmission.latestScore(),
                scorePublished ? experiment.publishedAt() : null
        );
    }

    @Transactional
    public LabEvaluationResultView evaluateSubmissionByTeacher(long labId, long submissionId, long teacherId) {
        SubmissionAccess access = verifySubmissionAccess(labId, submissionId, teacherId);
        if (!access.canManage()) {
            throw new LabPermissionException("无课程管理权限");
        }
        LabExperiment experiment = findExistingExperiment(labId);
        markSubmissionPendingEvaluation(access.submission(), experiment.testcases().size(), LocalDateTime.now());
        scheduleEvaluationAfterCommit(
                experiment,
                access.submission(),
                resolveSubmissionSource(access.submission())
        );
        return getSubmissionResult(labId, submissionId, teacherId);
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

    private String trustedSourceContentType(String contentType) {
        String normalized = normalizeContentType(contentType);
        return normalized == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : normalized;
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

    private LabExperiment findExistingExperiment(long labId) {
        return labExperimentRepository.findById(labId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("实验不存在"));
    }

    private SubmissionAccess verifySubmissionAccess(long labId, long submissionId, long userId) {
        LabExperiment experiment = findExistingExperiment(labId);
        LabSubmission submission = labSubmissionRepository.findById(submissionId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("提交不存在"));
        if (submission.labId() != labId) {
            throw new LabNotFoundException("提交不存在");
        }
        boolean canManage = coursePermissionClient.canManageCourse(experiment.courseId(), userId);
        if (!canManage) {
            requireCourseViewPermission(experiment.courseId(), userId);
            if (submission.studentId() != userId) {
                throw new LabPermissionException("无权限查看他人提交");
            }
        }
        return new SubmissionAccess(experiment, submission, canManage);
    }

    private void requireCourseViewPermission(long courseId, long userId) {
        if (!coursePermissionClient.canViewCourse(courseId, userId)) {
            throw new LabPermissionException("无课程访问权限");
        }
    }

    private List<LabSubmission> findStudentSubmissions(LabExperiment experiment, long userId, LabSubmissionQuery query) {
        requireCourseViewPermission(experiment.courseId(), userId);
        sanitizeStudentQuery(query, userId);
        return labSubmissionRepository.findByLabIdAndStudentId(experiment.id(), userId);
    }

    private LabSubmissionQuery sanitizeStudentQuery(LabSubmissionQuery query, long userId) {
        if (query.studentId() != null && query.studentId() != userId) {
            throw new LabPermissionException("学生只能查看本人提交");
        }
        return new LabSubmissionQuery(userId, query.submitStatus(), query.evaluationStatus(), query.overdue());
    }

    private List<LabSubmission> applyFilters(List<LabSubmission> submissions, LabExperiment experiment, LabSubmissionQuery query) {
        return submissions.stream()
                .filter(submission -> query.studentId() == null || submission.studentId() == query.studentId())
                .filter(submission -> query.submitStatus() == null || submission.submitStatus() == query.submitStatus())
                .filter(submission -> query.evaluationStatus() == null || submission.evaluationStatus() == query.evaluationStatus())
                .filter(submission -> query.overdue() == null
                        || isOverdue(submission, experiment.deadline()) == query.overdue())
                .toList();
    }

    private boolean isOverdue(LabSubmission submission, LocalDateTime deadline) {
        return submission.submittedAt().isAfter(deadline);
    }

    private Map<Long, SubmissionVersionFlags> buildSubmissionVersionFlags(List<LabSubmission> submissions) {
        Map<Long, LabSubmission> latestSubmissionByStudent = new HashMap<>();
        for (LabSubmission submission : submissions) {
            latestSubmissionByStudent.merge(
                    submission.studentId(),
                    submission,
                    (current, candidate) -> SUBMISSION_RECENCY.compare(candidate, current) > 0 ? candidate : current
            );
        }

        Map<Long, SubmissionVersionFlags> flagsBySubmissionId = new LinkedHashMap<>();
        for (LabSubmission submission : submissions) {
            LabSubmission latestSubmission = latestSubmissionByStudent.get(submission.studentId());
            boolean isLatest = latestSubmission != null && latestSubmission.id() == submission.id();
            flagsBySubmissionId.put(
                    submission.id(),
                    new SubmissionVersionFlags(isLatest, submission.isFinal(), submission.isFinal())
            );
        }
        return flagsBySubmissionId;
    }

    private SubmissionVersionFlags resolveSubmissionVersionFlags(
            Map<Long, SubmissionVersionFlags> flagsBySubmissionId,
            LabSubmission submission
    ) {
        return flagsBySubmissionId.getOrDefault(
                submission.id(),
                new SubmissionVersionFlags(false, submission.isFinal(), submission.isFinal())
        );
    }

    private List<LabSubmissionHistoryItemView> toHistoryItems(
            List<LabSubmission> submissions,
            Map<Long, SubmissionVersionFlags> flagsBySubmissionId,
            boolean scoresVisible
    ) {
        return submissions.stream()
                .map(submission -> toHistoryItem(
                        submission,
                        resolveSubmissionVersionFlags(flagsBySubmissionId, submission),
                        scoresVisible
                ))
                .toList();
    }

    private LabSubmissionHistoryItemView toHistoryItem(
            LabSubmission submission,
            SubmissionVersionFlags flags,
            boolean scoresVisible
    ) {
        return new LabSubmissionHistoryItemView(
                submission.id(),
                submission.labId(),
                submission.studentId(),
                submission.language(),
                submission.submitStatus(),
                submission.evaluationStatus(),
                submission.autoScore(),
                scoresVisible ? submission.finalScore() : null,
                submission.version(),
                submission.submittedAt(),
                flags.isLatest(),
                flags.isFinal(),
                flags.isScoringBasis(),
                hasFile(submission.fileId())
        );
    }

    private LabSubmissionDetailView toDetail(
            LabSubmission submission,
            SubmissionVersionFlags flags,
            long courseId
    ) {
        LabReportSummaryView latestReport = labReportService.findLatestReportForSubmission(submission.id())
                .orElse(null);
        var latestScore = labScoreService.findLatestScore(submission.id())
                .orElse(null);
        return new LabSubmissionDetailView(
                submission.id(),
                submission.labId(),
                submission.studentId(),
                submission.language(),
                submission.submitStatus(),
                submission.evaluationStatus(),
                submission.autoScore(),
                submission.finalScore(),
                submission.version(),
                submission.submittedAt(),
                flags.isLatest(),
                flags.isFinal(),
                flags.isScoringBasis(),
                hasFile(submission.fileId()),
                submission.codeContent(),
                resolveTrustedSourceFileView(submission, courseId),
                latestReport,
                latestScore
        );
    }

    private LabSubmissionDetailView toVisibleSubmissionDetail(
            LabSubmissionDetailView detail,
            boolean canManage,
            boolean scoresPublished
    ) {
        if (canManage) {
            return detail;
        }
        LabReportSummaryView visibleReport = scoresPublished
                ? detail.latestReport()
                : hideReportScore(detail.latestReport());
        return new LabSubmissionDetailView(
                detail.submissionId(),
                detail.labId(),
                detail.studentId(),
                detail.language(),
                detail.submitStatus(),
                detail.evaluationStatus(),
                detail.autoScore(),
                scoresPublished ? detail.finalScore() : null,
                detail.version(),
                detail.submittedAt(),
                detail.isLatest(),
                detail.isFinal(),
                detail.isScoringBasis(),
                detail.hasFile(),
                detail.code(),
                detail.sourceFile() == null ? null : detail.sourceFile().withDownloadAvailable(false),
                visibleReport,
                scoresPublished ? detail.latestScore() : null
        );
    }

    private LabSubmissionSourceFileView resolveTrustedSourceFileView(LabSubmission submission, long courseId) {
        if (!hasFile(submission.fileId())) {
            return null;
        }
        return labSubmissionSourceFileRepository.findBySubmissionId(submission.id())
                .filter(sourceFile -> sourceFile.status() == LabSubmissionSourceFileStatus.AVAILABLE)
                .filter(sourceFile -> hasValidSourceBinding(sourceFile, submission, courseId))
                .filter(sourceFile -> hasTrustedMetadata(sourceFile, submission.language()))
                .map(sourceFile -> new LabSubmissionSourceFileView(
                        sourceFile.originalFilename(),
                        sourceFile.contentType(),
                        sourceFile.fileSize(),
                        true
                ))
                .orElse(null);
    }

    private boolean hasValidSourceBinding(
            LabSubmissionSourceFile sourceFile,
            LabSubmission submission,
            long courseId
    ) {
        return sourceFile.submissionId() == submission.id()
                && sourceFile.labId() == submission.labId()
                && sourceFile.courseId() == courseId
                && sourceFile.uploaderId() == submission.studentId()
                && sourceFile.storageKey() != null
                && sourceFile.storageKey().equals(submission.fileId());
    }

    private boolean hasTrustedMetadata(LabSubmissionSourceFile sourceFile, String language) {
        if (!isSafeSourceFilename(sourceFile.originalFilename(), language)
                || sourceFile.fileSize() < 0
                || sourceFile.fileSize() > MAX_UPLOAD_SIZE_BYTES
                || sourceFile.deletedAt() != null) {
            return false;
        }
        String contentType = sourceFile.contentType();
        if (contentType == null || contentType.isBlank() || contentType.length() > 128
                || contentType.indexOf('\r') >= 0 || contentType.indexOf('\n') >= 0) {
            return false;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(contentType);
            return parsed.getParameters().isEmpty()
                    && isSupportedContentType(language, parsed.toString().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isSafeSourceFilename(String filename, String language) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0) {
            return false;
        }
        for (int index = 0; index < filename.length(); index++) {
            if (Character.isISOControl(filename.charAt(index))) {
                return false;
            }
        }
        Set<String> allowedExtensions = SOURCE_FILE_EXTENSIONS.get(language);
        return allowedExtensions != null && allowedExtensions.contains(extractExtension(filename));
    }

    private LabReportSummaryView hideReportScore(LabReportSummaryView report) {
        if (report == null) {
            return null;
        }
        return new LabReportSummaryView(
                report.reportId(),
                report.submissionId(),
                report.fileName(),
                report.fileType(),
                report.fileSize(),
                report.version(),
                null,
                null,
                report.submittedAt(),
                report.downloadUrl()
        );
    }

    private boolean areScoresPublished(LabExperiment experiment) {
        return experiment.status() == LabExperimentStatus.SCORE_PUBLISHED
                || experiment.status() == LabExperimentStatus.ARCHIVED;
    }

    private boolean hasFile(String fileId) {
        return fileId != null && !fileId.isBlank();
    }

    private String resolveSubmissionSource(LabSubmission submission) {
        if (submission.codeContent() != null && !submission.codeContent().isBlank()) {
            return submission.codeContent();
        }
        if (!hasFile(submission.fileId())) {
            throw new LabSubmissionValidationException("LAB-400-03", "提交代码不能为空且必须上传文件");
        }
        try {
            return new String(
                    fileStorageService.load(submission.fileId()).resource().getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("读取提交文件失败", exception);
        }
    }

    private String resolveEvaluationMessage(EvaluationStatus status, int passedCases, int totalCases) {
        if (status == EvaluationStatus.ACCEPTED) {
            return "全部用例通过";
        }
        if (status == EvaluationStatus.WRONG_ANSWER) {
            return passedCases == 0 ? "未通过任何用例" : "部分用例未通过";
        }
        if (status == EvaluationStatus.PENDING) {
            return "等待评测";
        }
        if (status == EvaluationStatus.RUNNING) {
            return "评测进行中";
        }
        if (status == EvaluationStatus.SYSTEM_ERROR) {
            return "评测失败";
        }
        return status.name();
    }

    private void markSubmissionPendingEvaluation(LabSubmission submission, int testcaseCount, LocalDateTime now) {
        labSubmissionRepository.update(submission.withEvaluationResult(EvaluationStatus.PENDING, null, submission.finalScore(), now));
        labEvaluationRepository.save(new LabEvaluation(
                0L,
                submission.id(),
                EvaluationStatus.PENDING,
                0,
                0,
                testcaseCount,
                null,
                null,
                "等待评测",
                null,
                null,
                now,
                null,
                now,
                now
        ));
    }

    private void finalizeEmptyAutoEvaluation(LabSubmission submission, LocalDateTime now) {
        labSubmissionRepository.update(submission.withEvaluationResult(EvaluationStatus.ACCEPTED, 100, submission.finalScore(), now));
        labEvaluationRepository.save(new LabEvaluation(
                0L,
                submission.id(),
                EvaluationStatus.ACCEPTED,
                100,
                0,
                0,
                0,
                null,
                "全部用例通过",
                null,
                null,
                now,
                now,
                now,
                now
        ));
    }

    private void scheduleEvaluationAfterCommit(LabExperiment experiment, LabSubmission submission, String sourceCode) {
        Runnable trigger = () -> labEvaluationService.evaluateSubmissionAsync(experiment, submission, sourceCode);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            trigger.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                trigger.run();
            }
        });
    }

    private void registerFileRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    fileStorageService.delete(storageKey);
                }
            }
        });
    }

    private record SubmissionVersionFlags(
            boolean isLatest,
            boolean isFinal,
            boolean isScoringBasis
    ) {
    }

    private record SubmissionAccess(
            LabExperiment experiment,
            LabSubmission submission,
            boolean canManage
    ) {
    }

}
