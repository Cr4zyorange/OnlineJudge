package com.onlinejudge.grd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshot;
import com.onlinejudge.grd.domain.GradeAnalysisSnapshotRepository;
import com.onlinejudge.grd.domain.GradeAnalysisSourceVersion;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeRecordRepository;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GradeAnalysisService {
    private static final List<String> TARGET_TYPES = List.of("COURSE_TOTAL", "GRADE_ITEM");
    private static final String ANALYSIS_CONTRACT_VERSION = "GRD_ANALYSIS_V2";
    private static final ObjectMapper SNAPSHOT_JSON = new ObjectMapper();
    private static final TypeReference<List<GradeScoreBucket>> DISTRIBUTION_TYPE = new TypeReference<>() {
    };

    private final GradeItemRepository gradeItemRepository;
    private final GradeRecordRepository gradeRecordRepository;
    private final CourseGradeSummaryRepository courseGradeSummaryRepository;
    private final GradeAnalysisSnapshotRepository gradeAnalysisSnapshotRepository;
    private final CoursePermissionClient coursePermissionClient;

    public GradeAnalysisService(
            GradeItemRepository gradeItemRepository,
            GradeRecordRepository gradeRecordRepository,
            CourseGradeSummaryRepository courseGradeSummaryRepository,
            GradeAnalysisSnapshotRepository gradeAnalysisSnapshotRepository,
            CoursePermissionClient coursePermissionClient
    ) {
        this.gradeItemRepository = gradeItemRepository;
        this.gradeRecordRepository = gradeRecordRepository;
        this.courseGradeSummaryRepository = courseGradeSummaryRepository;
        this.gradeAnalysisSnapshotRepository = gradeAnalysisSnapshotRepository;
        this.coursePermissionClient = coursePermissionClient;
    }

    @Transactional
    public GradeAnalysisResult analyzeCourseGrades(long courseId, long teacherId, String targetType, Long gradeItemId) {
        requireCoursePermission(courseId, teacherId);
        String normalizedTargetType = normalizeTargetType(targetType);
        if ("GRADE_ITEM".equals(normalizedTargetType)) {
            requireGradeItem(courseId, gradeItemId);
        } else {
            gradeItemId = null;
        }
        Set<Long> studentIds = studentIdsForAnalysis(courseId);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        ResolvedAnalysis analysis = resolveAnalysis(
                courseId,
                normalizedTargetType,
                gradeItemId,
                studentIds,
                teacherId,
                now
        );
        GradeAnalysisSnapshot snapshot = analysis.snapshot();
        return new GradeAnalysisResult(
                snapshot.targetType(),
                snapshot.gradeItemId(),
                snapshot.totalStudentCount(),
                snapshot.completedCount(),
                snapshot.missingCount(),
                snapshot.unsubmittedCount(),
                snapshot.ungradedCount(),
                snapshot.averageScore(),
                snapshot.maxScore(),
                snapshot.minScore(),
                snapshot.passRate(),
                snapshot.completionRate(),
                analysis.distribution(),
                snapshot.sourceDataTime(),
                snapshot.generatedAt()
        );
    }

    @Transactional
    public GradeItemCompletionResult getGradeItemCompletion(long courseId, long gradeItemId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        requireGradeItem(courseId, gradeItemId);
        Set<Long> studentIds = studentIdsForAnalysis(courseId);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        ResolvedAnalysis analysis = resolveAnalysis(
                courseId,
                "GRADE_ITEM",
                gradeItemId,
                studentIds,
                teacherId,
                now
        );
        GradeAnalysisSnapshot snapshot = analysis.snapshot();
        return new GradeItemCompletionResult(
                gradeItemId,
                snapshot.totalStudentCount(),
                snapshot.completedCount() + snapshot.ungradedCount(),
                snapshot.completedCount(),
                snapshot.missingCount(),
                snapshot.unsubmittedCount(),
                snapshot.ungradedCount(),
                snapshot.averageScore(),
                snapshot.completionRate(),
                snapshot.sourceDataTime(),
                snapshot.generatedAt()
        );
    }

    private ResolvedAnalysis resolveAnalysis(
            long courseId,
            String targetType,
            Long gradeItemId,
            Set<Long> studentIds,
            long teacherId,
            LocalDateTime now
    ) {
        GradeAnalysisSourceVersion sourceVersion = "GRADE_ITEM".equals(targetType)
                ? gradeRecordRepository.findAnalysisSourceVersion(courseId, gradeItemId)
                : courseGradeSummaryRepository.findAnalysisSourceVersion(courseId);
        String sourceFingerprint = sourceFingerprint(
                courseId,
                targetType,
                gradeItemId,
                studentIds,
                sourceVersion
        );
        Optional<GradeAnalysisSnapshot> latest = gradeAnalysisSnapshotRepository.findLatest(
                courseId,
                targetType,
                gradeItemId
        );
        Optional<ResolvedAnalysis> reusable = latest.flatMap(snapshot -> reusableAnalysis(snapshot, sourceFingerprint));
        if (reusable.isPresent()) {
            return reusable.orElseThrow();
        }
        List<ScoreRow> rows = "GRADE_ITEM".equals(targetType)
                ? gradeItemRows(courseId, gradeItemId, studentIds)
                : courseTotalRows(courseId, studentIds);
        List<BigDecimal> scores = rows.stream()
                .map(ScoreRow::score)
                .filter(score -> score != null)
                .toList();
        List<GradeScoreBucket> distribution = distribution(scores);
        LocalDateTime sourceDataTime = nextSourceDataTime(rows, latest, now);
        LocalDateTime generatedAt = nextGeneratedAt(latest, now);
        GradeAnalysisSnapshot snapshot = gradeAnalysisSnapshotRepository.save(new GradeAnalysisSnapshot(
                0L,
                courseId,
                targetType,
                gradeItemId,
                sourceDataTime,
                sourceFingerprint,
                average(scores),
                max(scores),
                min(scores),
                passRate(scores),
                rate(scores.size(), studentIds.size()),
                studentIds.size(),
                scores.size(),
                countByStatus(rows, ScoreStatus.MISSING),
                countByStatus(rows, ScoreStatus.UNSUBMITTED),
                countByStatus(rows, ScoreStatus.UNGRADED),
                distributionJson(distribution),
                teacherId,
                generatedAt
        ));
        return new ResolvedAnalysis(snapshot, distribution);
    }

    private Optional<ResolvedAnalysis> reusableAnalysis(
            GradeAnalysisSnapshot snapshot,
            String sourceFingerprint
    ) {
        if (!sourceFingerprint.equals(snapshot.sourceFingerprint())
                || snapshot.totalStudentCount() == null
                || snapshot.completedCount() == null
                || snapshot.missingCount() == null
                || snapshot.unsubmittedCount() == null
                || snapshot.ungradedCount() == null
                || snapshot.distributionJson() == null) {
            return Optional.empty();
        }
        try {
            List<GradeScoreBucket> distribution = SNAPSHOT_JSON.readValue(
                    snapshot.distributionJson(),
                    DISTRIBUTION_TYPE
            );
            return Optional.of(new ResolvedAnalysis(snapshot, distribution));
        } catch (JsonProcessingException invalidSnapshot) {
            return Optional.empty();
        }
    }

    private LocalDateTime nextSourceDataTime(
            List<ScoreRow> rows,
            Optional<GradeAnalysisSnapshot> latest,
            LocalDateTime now
    ) {
        LocalDateTime latestRowUpdate = rows.stream()
                .map(ScoreRow::updatedAt)
                .filter(updatedAt -> updatedAt != null)
                .map(updatedAt -> updatedAt.truncatedTo(ChronoUnit.SECONDS))
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latest.isEmpty()) {
            return latestRowUpdate == null ? now : latestRowUpdate;
        }
        LocalDateTime previousSourceTime = latest.orElseThrow().sourceDataTime();
        if (latestRowUpdate != null && latestRowUpdate.isAfter(previousSourceTime)) {
            return latestRowUpdate;
        }
        LocalDateTime minimumNextSourceTime = previousSourceTime.plusSeconds(1);
        return now.isAfter(minimumNextSourceTime) ? now : minimumNextSourceTime;
    }

    private LocalDateTime nextGeneratedAt(
            Optional<GradeAnalysisSnapshot> latest,
            LocalDateTime now
    ) {
        if (latest.isEmpty() || now.isAfter(latest.orElseThrow().generatedAt())) {
            return now;
        }
        return latest.orElseThrow().generatedAt().plusSeconds(1);
    }

    private List<ScoreRow> courseTotalRows(long courseId, Set<Long> studentIds) {
        Map<Long, CourseGradeSummary> summariesByStudent = courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(CourseGradeSummary::studentId, Function.identity(), (left, right) -> right));
        return studentIds.stream()
                .map(studentId -> {
                    CourseGradeSummary summary = summariesByStudent.get(studentId);
                    if (summary == null || summary.finalScore() == null || summary.finalStatus() == FinalStatus.INCOMPLETE) {
                        return new ScoreRow(
                                studentId,
                                null,
                                ScoreStatus.MISSING,
                                summary == null ? null : summary.updatedAt()
                        );
                    }
                    return new ScoreRow(
                            studentId,
                            summary.finalScore(),
                            ScoreStatus.COMPLETED,
                            summary.updatedAt()
                    );
                })
                .toList();
    }

    private List<ScoreRow> gradeItemRows(long courseId, Long gradeItemId, Set<Long> studentIds) {
        Map<Long, GradeRecord> recordsByStudent = gradeRecordRepository.findByCourseId(courseId).stream()
                .filter(record -> record.gradeItemId() == gradeItemId)
                .collect(Collectors.toMap(GradeRecord::studentId, Function.identity(), (left, right) -> right));
        return studentIds.stream()
                .map(studentId -> {
                    GradeRecord record = recordsByStudent.get(studentId);
                    if (record == null) {
                        return new ScoreRow(studentId, null, ScoreStatus.MISSING, null);
                    }
                    if ((record.gradeStatus() == GradeStatus.SCORED || record.gradeStatus() == GradeStatus.ADJUSTED)
                            && record.rawScore() != null) {
                        return new ScoreRow(
                                studentId,
                                record.rawScore(),
                                ScoreStatus.COMPLETED,
                                record.updatedAt()
                        );
                    }
                    return new ScoreRow(
                            studentId,
                            null,
                            toScoreStatus(record.gradeStatus()),
                            record.updatedAt()
                    );
                })
                .toList();
    }

    private Set<Long> studentIdsForAnalysis(long courseId) {
        return coursePermissionClient.listCourseStudentIds(courseId).stream()
                .filter(studentId -> studentId != null && studentId > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String sourceFingerprint(
            long courseId,
            String targetType,
            Long gradeItemId,
            Set<Long> studentIds,
            GradeAnalysisSourceVersion sourceVersion
    ) {
        StringBuilder source = new StringBuilder()
                .append("contract=").append(ANALYSIS_CONTRACT_VERSION)
                .append("|course=").append(courseId)
                .append("|target=").append(targetType)
                .append("|item=").append(gradeItemId == null ? "-" : gradeItemId)
                .append("|sourceVersion=").append(sourceVersion.version())
                .append("|sourceDataTime=").append(timeValue(sourceVersion.sourceDataTime()));
        studentIds.forEach(studentId -> source.append("|student=").append(studentId));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return ANALYSIS_CONTRACT_VERSION + ":"
                    + HexFormat.of().formatHex(digest.digest(source.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private String timeValue(LocalDateTime value) {
        return value == null ? "-" : value.toString();
    }

    private GradeItem requireGradeItem(long courseId, Long gradeItemId) {
        if (gradeItemId == null || gradeItemId <= 0) {
            throw new GradePublishException("成绩项统计必须指定成绩项");
        }
        GradeItem item = gradeItemRepository.findById(gradeItemId)
                .orElseThrow(() -> new GradeItemNotFoundException("成绩项不存在"));
        if (item.courseId() != courseId || !item.enabled()) {
            throw new GradeItemNotFoundException("成绩项不存在");
        }
        return item;
    }

    private String normalizeTargetType(String targetType) {
        String normalized = targetType == null || targetType.isBlank()
                ? "COURSE_TOTAL"
                : targetType.trim().toUpperCase();
        if (!TARGET_TYPES.contains(normalized)) {
            throw new GradePublishException("成绩统计目标不合法");
        }
        return normalized;
    }

    private List<GradeScoreBucket> distribution(List<BigDecimal> scores) {
        return List.of(
                new GradeScoreBucket("0-59", countRange(scores, "0", "60")),
                new GradeScoreBucket("60-69", countRange(scores, "60", "70")),
                new GradeScoreBucket("70-79", countRange(scores, "70", "80")),
                new GradeScoreBucket("80-89", countRange(scores, "80", "90")),
                new GradeScoreBucket("90-100", countRange(scores, "90", "100.0001"))
        );
    }

    private int countRange(List<BigDecimal> scores, String minInclusive, String maxExclusive) {
        BigDecimal min = new BigDecimal(minInclusive);
        BigDecimal max = new BigDecimal(maxExclusive);
        return (int) scores.stream()
                .filter(score -> score.compareTo(min) >= 0 && score.compareTo(max) < 0)
                .count();
    }

    private BigDecimal average(List<BigDecimal> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(new BigDecimal(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal max(List<BigDecimal> scores) {
        return scores.stream().max(BigDecimal::compareTo).orElse(null);
    }

    private BigDecimal min(List<BigDecimal> scores) {
        return scores.stream().min(BigDecimal::compareTo).orElse(null);
    }

    private BigDecimal passRate(List<BigDecimal> scores) {
        if (scores.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        int passed = (int) scores.stream()
                .filter(score -> score.compareTo(new BigDecimal("60.00")) >= 0)
                .count();
        return rate(passed, scores.size());
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), 4, RoundingMode.HALF_UP);
    }

    private int countByStatus(List<ScoreRow> rows, ScoreStatus status) {
        return (int) rows.stream().filter(row -> row.status() == status).count();
    }

    private ScoreStatus toScoreStatus(GradeStatus gradeStatus) {
        return switch (gradeStatus) {
            case UNSUBMITTED -> ScoreStatus.UNSUBMITTED;
            case UNGRADED -> ScoreStatus.UNGRADED;
            case MISSING -> ScoreStatus.MISSING;
            case SCORED, ADJUSTED -> ScoreStatus.COMPLETED;
        };
    }

    private String distributionJson(List<GradeScoreBucket> distribution) {
        return distribution.stream()
                .map(bucket -> "{\"label\":\"" + bucket.label() + "\",\"count\":" + bucket.count() + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void requireCoursePermission(long courseId, long teacherId) {
        if (!coursePermissionClient.canManageCourse(courseId, teacherId)) {
            throw new GradeItemPermissionException("教师无课程成绩管理权限");
        }
    }

    private record ScoreRow(
            long studentId,
            BigDecimal score,
            ScoreStatus status,
            LocalDateTime updatedAt
    ) {
    }

    private record ResolvedAnalysis(
            GradeAnalysisSnapshot snapshot,
            List<GradeScoreBucket> distribution
    ) {
    }

    private enum ScoreStatus {
        COMPLETED,
        MISSING,
        UNSUBMITTED,
        UNGRADED
    }
}
