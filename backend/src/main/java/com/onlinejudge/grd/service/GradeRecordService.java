package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.FinalStatus;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GradeRecordService {
    private final GradeItemRepository gradeItemRepository;
    private final GradeRecordRepository gradeRecordRepository;
    private final CourseGradeSummaryRepository courseGradeSummaryRepository;
    private final SourceGradeClient sourceGradeClient;
    private final CoursePermissionClient coursePermissionClient;

    public GradeRecordService(
            GradeItemRepository gradeItemRepository,
            GradeRecordRepository gradeRecordRepository,
            CourseGradeSummaryRepository courseGradeSummaryRepository,
            SourceGradeClient sourceGradeClient,
            CoursePermissionClient coursePermissionClient
    ) {
        this.gradeItemRepository = gradeItemRepository;
        this.gradeRecordRepository = gradeRecordRepository;
        this.courseGradeSummaryRepository = courseGradeSummaryRepository;
        this.sourceGradeClient = sourceGradeClient;
        this.coursePermissionClient = coursePermissionClient;
    }

    public GradeSyncResult syncSourceGrades(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        List<GradeItem> sourceItems = sourceGradeItems(courseId);
        LocalDateTime now = LocalDateTime.now();
        int syncedCount = 0;
        int missingCount = 0;
        int ungradedCount = 0;

        for (GradeItem item : sourceItems) {
            List<SourceGradeDTO> sourceGrades = sourceGradeClient.findSourceGrades(
                    courseId,
                    SourceGradeType.valueOf(item.sourceType().name()),
                    item.sourceId()
            );
            if (sourceGrades.isEmpty()) {
                missingCount++;
            }
            for (SourceGradeDTO sourceGrade : sourceGrades) {
                GradeRecord record = toGradeRecord(item, sourceGrade, now);
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

        int affectedCount = recalculateCourseGrades(courseId, teacherId).affectedCount();
        return new GradeSyncResult(0L, sourceItems.size(), affectedCount, syncedCount, missingCount, ungradedCount);
    }

    public GradeRecalculationResult recalculateCourseGrades(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        Map<Long, GradeItem> itemsById = gradeItemRepository.findByCourseId(courseId).stream()
                .filter(GradeItem::enabled)
                .collect(Collectors.toMap(GradeItem::id, item -> item));
        Map<Long, List<GradeRecord>> recordsByStudent = gradeRecordRepository.findByCourseId(courseId).stream()
                .collect(Collectors.groupingBy(GradeRecord::studentId));
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Long, List<GradeRecord>> entry : recordsByStudent.entrySet()) {
            long studentId = entry.getKey();
            List<GradeRecord> includedRecords = entry.getValue().stream()
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
                    PublishStatus.UNPUBLISHED,
                    0L,
                    null,
                    now,
                    now
            ));
        }
        return new GradeRecalculationResult(0L, recordsByStudent.size());
    }

    public List<CourseGradeRow> listCourseGrades(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        Map<Long, List<GradeRecord>> recordsByStudent = gradeRecordRepository.findByCourseId(courseId).stream()
                .collect(Collectors.groupingBy(GradeRecord::studentId));
        Map<Long, CourseGradeSummary> summariesByStudent = courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(CourseGradeSummary::studentId, summary -> summary));
        Set<Long> studentIds = new LinkedHashSet<>();
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
}
