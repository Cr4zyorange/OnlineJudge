package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
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
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionDetailView;
import com.onlinejudge.lab.domain.LabSubmissionHistoryItemView;
import com.onlinejudge.lab.domain.LabSubmissionQuery;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
            "python", Set.of("text/x-python", "application/x-python-code"),
            "cpp", Set.of("text/x-c++src", "text/x-c++source", "application/x-c++src"),
            "c", Set.of("text/x-csrc", "text/x-c")
    );

    private final LabExperimentRepository labExperimentRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabEvaluationRepository labEvaluationRepository;
    private final LabEvaluationResultRepository labEvaluationResultRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final FileStorageService fileStorageService;
    private final Evaluator evaluator;

    public LabSubmissionService(
            LabExperimentRepository labExperimentRepository,
            LabSubmissionRepository labSubmissionRepository,
            LabEvaluationRepository labEvaluationRepository,
            LabEvaluationResultRepository labEvaluationResultRepository,
            CoursePermissionClient coursePermissionClient,
            FileStorageService fileStorageService,
            Evaluator evaluator
    ) {
        this.labExperimentRepository = labExperimentRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labEvaluationRepository = labEvaluationRepository;
        this.labEvaluationResultRepository = labEvaluationResultRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.fileStorageService = fileStorageService;
        this.evaluator = evaluator;
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

        LabSubmission savedSubmission = labSubmissionRepository.save(submission);
        if (!experiment.autoEvaluate() || experiment.testcases().isEmpty()) {
            return savedSubmission;
        }
        markSubmissionPendingEvaluation(savedSubmission, experiment.testcases().size(), now);
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
        return toHistoryItems(applyFilters(submissions, experiment, effectiveQuery), flagsBySubmissionId);
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
        return toDetail(submission, resolveSubmissionVersionFlags(flagsBySubmissionId, submission));
    }

    public LabEvaluationResultView getSubmissionResult(long labId, long submissionId, long userId) {
        SubmissionAccess access = verifySubmissionAccess(labId, submissionId, userId);
        LabSubmission submission = access.submission();
        List<LabEvaluationCaseResult> caseResults = labEvaluationResultRepository.findBySubmissionId(submissionId);
        if (!access.canManage()) {
            caseResults = caseResults.stream().map(LabEvaluationCaseResult::hideSensitiveContent).toList();
        }
        int passedCases = (int) caseResults.stream().filter(LabEvaluationCaseResult::passed).count();
        int totalCases = caseResults.size();
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
                caseResults,
                submission.submittedAt(),
                evaluation.finishedAt() == null ? submission.updatedAt() : evaluation.finishedAt()
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
        LabSubmission evaluated = evaluateSubmission(experiment, access.submission());
        return getSubmissionResult(labId, evaluated.id(), teacherId);
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
            Map<Long, SubmissionVersionFlags> flagsBySubmissionId
    ) {
        return submissions.stream()
                .map(submission -> toHistoryItem(submission, resolveSubmissionVersionFlags(flagsBySubmissionId, submission)))
                .toList();
    }

    private LabSubmissionHistoryItemView toHistoryItem(
            LabSubmission submission,
            SubmissionVersionFlags flags
    ) {
        return new LabSubmissionHistoryItemView(
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
                hasFile(submission.fileId())
        );
    }

    private LabSubmissionDetailView toDetail(
            LabSubmission submission,
            SubmissionVersionFlags flags
    ) {
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
                submission.fileId()
        );
    }

    private boolean hasFile(String fileId) {
        return fileId != null && !fileId.isBlank();
    }

    private LabSubmission evaluateSubmission(LabExperiment experiment, LabSubmission submission) {
        LocalDateTime startedAt = LocalDateTime.now();
        LabEvaluation createdEvaluation = labEvaluationRepository.save(new LabEvaluation(
                0L,
                submission.id(),
                EvaluationStatus.RUNNING,
                0,
                0,
                experiment.testcases().size(),
                null,
                null,
                "评测进行中",
                null,
                null,
                startedAt,
                null,
                startedAt,
                startedAt
        ));
        List<LabTestcaseEvaluationProjection> testcaseEvaluations = evaluateAgainstTestcases(experiment, submission, startedAt);
        List<LabEvaluationCaseResult> caseResults = testcaseEvaluations.stream()
                .map(item -> item.result)
                .toList();
        labEvaluationResultRepository.replaceSubmissionResults(submission.id(), caseResults);

        int autoScore = caseResults.stream().mapToInt(LabEvaluationCaseResult::score).sum();
        EvaluationStatus finalStatus = resolveFinalStatus(caseResults);
        int passedCases = (int) caseResults.stream().filter(LabEvaluationCaseResult::passed).count();
        LocalDateTime finishedAt = LocalDateTime.now();
        labEvaluationRepository.update(new LabEvaluation(
                createdEvaluation.id(),
                submission.id(),
                finalStatus,
                autoScore,
                passedCases,
                caseResults.size(),
                Math.toIntExact(java.time.Duration.between(startedAt, finishedAt).toMillis()),
                null,
                resolveEvaluationMessage(finalStatus, passedCases, caseResults.size()),
                null,
                caseResults.stream().map(LabEvaluationCaseResult::message).reduce((left, right) -> left + "\n" + right).orElse(null),
                startedAt,
                finishedAt,
                createdEvaluation.createdAt(),
                finishedAt
        ));
        LabSubmission evaluatedSubmission = submission.withEvaluationResult(
                finalStatus,
                autoScore,
                submission.finalScore(),
                finishedAt
        );
        return labSubmissionRepository.update(evaluatedSubmission);
    }

    private List<LabTestcaseEvaluationProjection> evaluateAgainstTestcases(
            LabExperiment experiment,
            LabSubmission submission,
            LocalDateTime startedAt
    ) {
        String sourceCode = resolveSubmissionSource(submission);
        List<LabTestcaseEvaluationProjection> evaluations = new ArrayList<>();
        for (int index = 0; index < experiment.testcases().size(); index += 1) {
            var testcase = experiment.testcases().get(index);
            var evaluationTask = new EvaluationTask(
                    submission.id() + "-" + testcase.id(),
                    "LAB",
                    experiment.courseId(),
                    experiment.id(),
                    submission.id(),
                    submission.studentId(),
                    submission.language(),
                    sourceCode,
                    Map.of(
                            "stdin", testcase.input(),
                            "expectedOutput", testcase.expectedOutput(),
                            "timeLimitMs", Integer.toString(testcase.timeLimitMs()),
                            "memoryLimitKb", Integer.toString(testcase.memoryLimitKb())
                    ),
                    submission.submittedAt()
            );
            var evaluationResult = evaluator.evaluate(evaluationTask);
            String actualOutput = evaluationResult.caseResults().isEmpty() ? "" : normalizeOutput(evaluationResult.caseResults().get(0));
            boolean passed = evaluationResult.status() == EvaluationStatus.ACCEPTED;
            int score = passed ? testcase.scoreWeight() : 0;
            evaluations.add(new LabTestcaseEvaluationProjection(new LabEvaluationCaseResult(
                    0L,
                    submission.id(),
                    testcase.id(),
                    testcase.orderNum(),
                    testcase.isPublic(),
                    evaluationResult.status(),
                    passed,
                    score,
                    testcase.input(),
                    testcase.expectedOutput(),
                    actualOutput,
                    evaluationResult.message(),
                    evaluationResult.finishedAt(),
                    startedAt,
                    evaluationResult.finishedAt()
            )));
        }
        return evaluations;
    }

    private EvaluationStatus resolveFinalStatus(List<LabEvaluationCaseResult> caseResults) {
        if (caseResults.isEmpty()) {
            return EvaluationStatus.PENDING;
        }
        boolean hasSystemFailure = caseResults.stream().anyMatch(result ->
                result.status() == EvaluationStatus.SYSTEM_ERROR
                        || result.status() == EvaluationStatus.RUNTIME_ERROR
                        || result.status() == EvaluationStatus.COMPILE_ERROR
                        || result.status() == EvaluationStatus.TIME_LIMIT_EXCEEDED
        );
        if (hasSystemFailure) {
            return EvaluationStatus.SYSTEM_ERROR;
        }
        return caseResults.stream().allMatch(LabEvaluationCaseResult::passed)
                ? EvaluationStatus.ACCEPTED
                : EvaluationStatus.WRONG_ANSWER;
    }

    private String normalizeOutput(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").trim();
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

    private record LabTestcaseEvaluationProjection(
            LabEvaluationCaseResult result
    ) {
    }
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
