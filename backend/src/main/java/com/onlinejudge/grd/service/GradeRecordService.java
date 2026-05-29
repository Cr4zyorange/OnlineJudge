package com.onlinejudge.grd.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.GradeChangeLog;
import com.onlinejudge.grd.domain.GradeChangeLogRepository;
import com.onlinejudge.grd.domain.GradeCalculationBatch;
import com.onlinejudge.grd.domain.GradeCalculationBatchRepository;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeRecordRepository;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GradeRecordService {
    private final GradeItemRepository gradeItemRepository;
    private final GradeRecordRepository gradeRecordRepository;
    private final GradeChangeLogRepository gradeChangeLogRepository;
    private final CourseGradeSummaryRepository courseGradeSummaryRepository;
    private final GradeCalculationBatchRepository gradeCalculationBatchRepository;
    private final SourceGradeClient sourceGradeClient;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public GradeRecordService(
            GradeItemRepository gradeItemRepository,
            GradeRecordRepository gradeRecordRepository,
            GradeChangeLogRepository gradeChangeLogRepository,
            CourseGradeSummaryRepository courseGradeSummaryRepository,
            GradeCalculationBatchRepository gradeCalculationBatchRepository,
            SourceGradeClient sourceGradeClient,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.gradeItemRepository = gradeItemRepository;
        this.gradeRecordRepository = gradeRecordRepository;
        this.gradeChangeLogRepository = gradeChangeLogRepository;
        this.courseGradeSummaryRepository = courseGradeSummaryRepository;
        this.gradeCalculationBatchRepository = gradeCalculationBatchRepository;
        this.sourceGradeClient = sourceGradeClient;
        this.coursePermissionClient = coursePermissionClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    public GradeSyncResult syncSourceGrades(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        List<GradeItem> sourceItems = sourceGradeItems(courseId);
        LocalDateTime now = LocalDateTime.now();
        int syncedCount = 0;
        int missingCount = 0;
        int ungradedCount = 0;
        Set<Long> studentIds = courseStudentIds(courseId);
        Map<GradeItem, Map<Long, SourceGradeDTO>> sourceGradesByItem = new LinkedHashMap<>();

        for (GradeItem item : sourceItems) {
            List<SourceGradeDTO> sourceGrades = sourceGradeClient.findSourceGrades(
                    courseId,
                    SourceGradeType.valueOf(item.sourceType().name()),
                    item.sourceId()
            );
            Map<Long, SourceGradeDTO> sourceGradesByStudent = sourceGrades.stream()
                    .collect(Collectors.toMap(SourceGradeDTO::studentId, sourceGrade -> sourceGrade, (left, right) -> right, LinkedHashMap::new));
            studentIds.addAll(sourceGradesByStudent.keySet());
            sourceGradesByItem.put(item, sourceGradesByStudent);
        }

        for (Map.Entry<GradeItem, Map<Long, SourceGradeDTO>> entry : sourceGradesByItem.entrySet()) {
            GradeItem item = entry.getKey();
            Map<Long, SourceGradeDTO> sourceGradesByStudent = entry.getValue();
            for (long studentId : studentIds) {
                GradeRecord record = sourceGradesByStudent.containsKey(studentId)
                        ? toGradeRecord(item, sourceGradesByStudent.get(studentId), now)
                        : missingGradeRecord(item, studentId, now);
                gradeRecordRepository.upsert(record);
                if (record.gradeStatus() == GradeStatus.SCORED) {
                    syncedCount++;
                } else if (record.gradeStatus() == GradeStatus.UNGRADED) {
                    ungradedCount++;
                } else {
                    missingCount++;
                }
            }
        }

        GradeCalculationBatch batch = saveCalculationBatch(
                courseId,
                "SYNC",
                sourceItems.size(),
                studentIds.size(),
                teacherId,
                now
        );
        int affectedCount = recalculateCourseGradesWithBatch(courseId, batch.id()).affectedCount();
        return new GradeSyncResult(batch.id(), sourceItems.size(), affectedCount, syncedCount, missingCount, ungradedCount);
    }

    public GradeRecalculationResult recalculateCourseGrades(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        Set<Long> studentIds = studentIdsForCalculation(courseId);
        GradeCalculationBatch batch = saveCalculationBatch(
                courseId,
                "RECALCULATE",
                includedGradeItems(courseId).size(),
                studentIds.size(),
                teacherId,
                LocalDateTime.now()
        );
        return recalculateCourseGradesWithBatch(courseId, batch.id());
    }

    private GradeRecalculationResult recalculateCourseGradesWithBatch(long courseId, long calculationBatchId) {
        Map<Long, GradeItem> itemsById = gradeItemRepository.findByCourseId(courseId).stream()
                .filter(GradeItem::enabled)
                .collect(Collectors.toMap(GradeItem::id, item -> item));
        Map<Long, List<GradeRecord>> recordsByStudent = gradeRecordRepository.findByCourseId(courseId).stream()
                .collect(Collectors.groupingBy(GradeRecord::studentId));
        Map<Long, CourseGradeSummary> summariesByStudent = courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(CourseGradeSummary::studentId, summary -> summary));
        Set<Long> studentIds = new LinkedHashSet<>();
        studentIds.addAll(courseStudentIds(courseId));
        studentIds.addAll(recordsByStudent.keySet());
        studentIds.addAll(summariesByStudent.keySet());
        LocalDateTime now = LocalDateTime.now();
        for (long studentId : studentIds) {
            CourseGradeSummary existingSummary = summariesByStudent.get(studentId);
            List<GradeRecord> includedRecords = recordsByStudent.getOrDefault(studentId, List.of()).stream()
                    .filter(record -> {
                        GradeItem item = itemsById.get(record.gradeItemId());
                        return item != null && item.includedInFinal();
                    })
                    .toList();
            boolean complete = !includedRecords.isEmpty() && includedRecords.stream()
                    .allMatch(record -> record.gradeStatus() == GradeStatus.SCORED || record.gradeStatus() == GradeStatus.ADJUSTED);
            BigDecimal finalScore = complete
                    ? includedRecords.stream()
                    .map(GradeRecord::weightedScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP)
                    : null;
            courseGradeSummaryRepository.upsert(new CourseGradeSummary(
                    0L,
                    courseId,
                    studentId,
                    finalScore,
                    complete ? FinalStatus.CALCULATED : FinalStatus.INCOMPLETE,
                    existingSummary == null ? PublishStatus.UNPUBLISHED : existingSummary.publishStatus(),
                    calculationBatchId,
                    existingSummary == null ? null : existingSummary.publishedAt(),
                    existingSummary == null ? now : existingSummary.createdAt(),
                    now
            ));
        }
        return new GradeRecalculationResult(calculationBatchId, studentIds.size());
    }

    public CourseGradeTablePage listCourseGrades(long courseId, long teacherId, GradeTableQuery query) {
        requireCoursePermission(courseId, teacherId);
        List<CourseGradeRow> filteredRows = buildCourseGradeRows(courseId).stream()
                .filter(row -> matchesStudentKeyword(row, query.studentKeyword()))
                .filter(row -> matchesGradeItem(row, query.gradeItemId()))
                .filter(row -> matchesGradeStatus(row, query.gradeStatus()))
                .filter(row -> matchesPublishStatus(row, query.publishStatus()))
                .toList();
        int total = filteredRows.size();
        int fromIndex = Math.min((query.page() - 1) * query.size(), total);
        int toIndex = Math.min(fromIndex + query.size(), total);
        return new CourseGradeTablePage(filteredRows.subList(fromIndex, toIndex), total, query.page(), query.size());
    }

    public List<CourseGradeRow> listCourseGrades(long courseId, long teacherId) {
        return listCourseGrades(courseId, teacherId, GradeTableQuery.firstPage()).records();
    }

    private List<CourseGradeRow> buildCourseGradeRows(long courseId) {
        Map<Long, List<GradeRecord>> recordsByStudent = gradeRecordRepository.findByCourseId(courseId).stream()
                .collect(Collectors.groupingBy(GradeRecord::studentId));
        Map<Long, CourseGradeSummary> summariesByStudent = courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(CourseGradeSummary::studentId, summary -> summary));
        Set<Long> studentIds = new LinkedHashSet<>();
        studentIds.addAll(courseStudentIds(courseId));
        studentIds.addAll(recordsByStudent.keySet());
        studentIds.addAll(summariesByStudent.keySet());
        return studentIds.stream()
                .sorted()
                .map(studentId -> new CourseGradeRow(
                        studentId,
                        summariesByStudent.get(studentId),
                        recordsByStudent.getOrDefault(studentId, List.of()).stream()
                                .sorted(Comparator.comparingLong(GradeRecord::gradeItemId))
                                .toList()
                ))
                .toList();
    }

    public CourseGradeRow getStudentGradeDetail(long courseId, long studentId, long teacherId) {
        return listCourseGrades(courseId, teacherId).stream()
                .filter(row -> row.studentId() == studentId)
                .findFirst()
                .orElse(new CourseGradeRow(studentId, null, List.of()));
    }

    @Transactional
    public GradeAdjustmentResult adjustGradeRecord(long recordId, long teacherId, AdjustGradeRecordCommand command) {
        GradeRecord record = gradeRecordRepository.findById(recordId)
                .orElseThrow(() -> new GradeItemNotFoundException("成绩记录不存在"));
        requireCoursePermission(record.courseId(), teacherId);
        GradeItem gradeItem = gradeItemRepository.findById(record.gradeItemId())
                .orElseThrow(() -> new GradeItemNotFoundException("成绩项不存在"));
        String reason = normalizeReason(command.reason());
        BigDecimal newScore = normalizeManualScore(command.newScore(), gradeItem.fullScore());
        LocalDateTime now = LocalDateTime.now();
        BigDecimal oldScore = record.rawScore();
        BigDecimal weightedScore = newScore.multiply(gradeItem.weight()).setScale(2, RoundingMode.HALF_UP);
        GradeRecord adjustedRecord = gradeRecordRepository.update(record.adjusted(newScore, weightedScore, now));
        GradeChangeLog changeLog = gradeChangeLogRepository.save(new GradeChangeLog(
                0L,
                adjustedRecord.courseId(),
                adjustedRecord.studentId(),
                adjustedRecord.gradeItemId(),
                "RECORD_ADJUST",
                oldScore,
                newScore,
                reason,
                teacherId,
                now
        ));
        if (record.publishStatus() == PublishStatus.PUBLISHED) {
            publishGradeChangedEvent(changeLog, "GRADE_ITEM", adjustedRecord.gradeItemId(), now);
        }
        GradeCalculationBatch batch = saveCalculationBatch(
                adjustedRecord.courseId(),
                "ADJUST_RECORD",
                1,
                1,
                teacherId,
                now
        );
        recalculateCourseGradesWithBatch(adjustedRecord.courseId(), batch.id());
        return new GradeAdjustmentResult(
                adjustedRecord.id(),
                adjustedRecord.studentId(),
                adjustedRecord.gradeItemId(),
                oldScore,
                newScore,
                reason,
                adjustedRecord.updatedAt()
        );
    }

    @Transactional
    public FinalScoreAdjustmentResult adjustCourseFinalScore(long summaryId, long teacherId, AdjustGradeRecordCommand command) {
        CourseGradeSummary summary = courseGradeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new GradeItemNotFoundException("课程总评不存在"));
        requireCoursePermission(summary.courseId(), teacherId);
        String reason = normalizeReason(command.reason());
        BigDecimal newScore = normalizeManualScore(command.newScore(), new BigDecimal("100.00"));
        LocalDateTime now = LocalDateTime.now();
        BigDecimal oldScore = summary.finalScore();
        CourseGradeSummary adjustedSummary = courseGradeSummaryRepository.update(summary.adjusted(newScore, now));
        GradeChangeLog changeLog = gradeChangeLogRepository.save(new GradeChangeLog(
                0L,
                adjustedSummary.courseId(),
                adjustedSummary.studentId(),
                null,
                "FINAL_ADJUST",
                oldScore,
                newScore,
                reason,
                teacherId,
                now
        ));
        if (summary.publishStatus() == PublishStatus.PUBLISHED) {
            publishGradeChangedEvent(changeLog, "COURSE_GRADE_SUMMARY", adjustedSummary.id(), now);
        }
        return new FinalScoreAdjustmentResult(
                adjustedSummary.id(),
                adjustedSummary.studentId(),
                oldScore,
                newScore,
                reason,
                adjustedSummary.updatedAt()
        );
    }

    private void publishGradeChangedEvent(
            GradeChangeLog changeLog,
            String targetType,
            long targetId,
            LocalDateTime changedAt
    ) {
        notificationEventPublisher.publish(new NotificationEvent(
                "GRD:GRADE_CHANGED:LOG:" + changeLog.id(),
                "GRADE_CHANGED",
                changeLog.courseId(),
                List.of(changeLog.studentId()),
                "成绩已变更",
                "课程成绩已调整，请查看最新成绩。",
                targetType,
                targetId,
                "/courses/" + changeLog.courseId() + "?page=grades",
                changedAt
        ));
    }

    public GradeChangeLogPage listGradeChangeLogs(
            long courseId,
            long teacherId,
            Long studentId,
            Long gradeItemId,
            int page,
            int size
    ) {
        requireCoursePermission(courseId, teacherId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        return new GradeChangeLogPage(
                gradeChangeLogRepository.findByCourseId(courseId, studentId, gradeItemId, normalizedPage, normalizedSize),
                gradeChangeLogRepository.countByCourseId(courseId, studentId, gradeItemId),
                normalizedPage,
                normalizedSize
        );
    }

    private List<GradeItem> sourceGradeItems(long courseId) {
        return gradeItemRepository.findByCourseId(courseId).stream()
                .filter(GradeItem::enabled)
                .filter(GradeItem::includedInFinal)
                .filter(item -> item.sourceId() != null && item.sourceId() > 0)
                .filter(item -> item.sourceType() == SourceType.LAB || item.sourceType() == SourceType.HWK)
                .toList();
    }

    private GradeRecord toGradeRecord(GradeItem item, SourceGradeDTO sourceGrade, LocalDateTime now) {
        GradeStatus status = toGradeStatus(sourceGrade);
        BigDecimal rawScore = null;
        BigDecimal weightedScore = null;
        if (status == GradeStatus.SCORED) {
            rawScore = normalizeRawScore(item, sourceGrade);
            weightedScore = rawScore.multiply(item.weight()).setScale(2, RoundingMode.HALF_UP);
        }
        return new GradeRecord(
                0L,
                item.courseId(),
                sourceGrade.studentId(),
                item.id(),
                item.sourceType(),
                item.sourceId(),
                rawScore,
                weightedScore,
                status,
                PublishStatus.UNPUBLISHED,
                null,
                sourceGrade.updatedAt(),
                now,
                null,
                now,
                now
        );
    }

    private GradeRecord missingGradeRecord(GradeItem item, long studentId, LocalDateTime now) {
        return new GradeRecord(
                0L,
                item.courseId(),
                studentId,
                item.id(),
                item.sourceType(),
                item.sourceId(),
                null,
                null,
                GradeStatus.MISSING,
                PublishStatus.UNPUBLISHED,
                null,
                null,
                now,
                null,
                now,
                now
        );
    }

    private BigDecimal normalizeRawScore(GradeItem item, SourceGradeDTO sourceGrade) {
        if (sourceGrade.score() == null || sourceGrade.fullScore() == null || sourceGrade.fullScore().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return sourceGrade.score()
                .multiply(item.fullScore())
                .divide(sourceGrade.fullScore(), 2, RoundingMode.HALF_UP);
    }

    private GradeStatus toGradeStatus(SourceGradeDTO sourceGrade) {
        String status = sourceGrade.status() == null ? "" : sourceGrade.status().trim().toUpperCase();
        return switch (status) {
            case "SCORED", "ACCEPTED" -> sourceGrade.score() == null ? GradeStatus.MISSING : GradeStatus.SCORED;
            case "UNSUBMITTED" -> GradeStatus.UNSUBMITTED;
            case "UNGRADED", "PENDING", "RUNNING" -> GradeStatus.UNGRADED;
            default -> GradeStatus.MISSING;
        };
    }

    private void requireCoursePermission(long courseId, long teacherId) {
        if (!coursePermissionClient.canManageCourseGrade(courseId, teacherId)) {
            throw new GradeItemPermissionException("教师无课程成绩管理权限");
        }
    }

    private Set<Long> courseStudentIds(long courseId) {
        return coursePermissionClient.listCourseStudentIds(courseId).stream()
                .filter(studentId -> studentId != null && studentId > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new GradeAdjustmentException("已发布成绩修改或手动调整必须填写原因");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new GradeAdjustmentException("成绩调整原因不能超过 500 个字符");
        }
        return normalized;
    }

    private BigDecimal normalizeManualScore(BigDecimal score, BigDecimal fullScore) {
        if (score == null) {
            throw new GradeAdjustmentException("调整后成绩不能为空");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(fullScore) > 0) {
            throw new GradeAdjustmentException("调整后成绩必须在 0 到满分之间");
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private Set<Long> studentIdsForCalculation(long courseId) {
        Set<Long> studentIds = courseStudentIds(courseId);
        studentIds.addAll(gradeRecordRepository.findByCourseId(courseId).stream()
                .map(GradeRecord::studentId)
                .toList());
        studentIds.addAll(courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .map(CourseGradeSummary::studentId)
                .toList());
        return studentIds;
    }

    private List<GradeItem> includedGradeItems(long courseId) {
        return gradeItemRepository.findByCourseId(courseId).stream()
                .filter(GradeItem::enabled)
                .filter(GradeItem::includedInFinal)
                .toList();
    }

    private GradeCalculationBatch saveCalculationBatch(
            long courseId,
            String triggerType,
            int affectedItemCount,
            int affectedStudentCount,
            long calculatedBy,
            LocalDateTime calculatedAt
    ) {
        return gradeCalculationBatchRepository.save(new GradeCalculationBatch(
                0L,
                courseId,
                triggerType,
                affectedItemCount,
                affectedStudentCount,
                "SUCCESS",
                "course grades calculated",
                calculatedBy,
                calculatedAt
        ));
    }

    private boolean matchesStudentKeyword(CourseGradeRow row, String studentKeyword) {
        return studentKeyword == null || Long.toString(row.studentId()).contains(studentKeyword.trim());
    }

    private boolean matchesGradeItem(CourseGradeRow row, Long gradeItemId) {
        return gradeItemId == null || row.records().stream()
                .anyMatch(record -> record.gradeItemId() == gradeItemId);
    }

    private boolean matchesGradeStatus(CourseGradeRow row, GradeStatus gradeStatus) {
        return gradeStatus == null || row.records().stream()
                .anyMatch(record -> record.gradeStatus() == gradeStatus);
    }

    private boolean matchesPublishStatus(CourseGradeRow row, PublishStatus publishStatus) {
        if (publishStatus == null) {
            return true;
        }
        boolean summaryMatches = row.summary() != null && row.summary().publishStatus() == publishStatus;
        return summaryMatches || row.records().stream()
                .anyMatch(record -> record.publishStatus() == publishStatus);
    }
}
