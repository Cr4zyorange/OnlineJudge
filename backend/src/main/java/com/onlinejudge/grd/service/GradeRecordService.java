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
import com.onlinejudge.grd.domain.GradePublishRecord;
import com.onlinejudge.grd.domain.GradePublishRecordRepository;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeRecordRepository;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
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
    private final GradePublishRecordRepository gradePublishRecordRepository;
    private final CourseGradeSummaryRepository courseGradeSummaryRepository;
    private final GradeCalculationBatchRepository gradeCalculationBatchRepository;
    private final SourceGradeClient sourceGradeClient;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;
    private GradeResultTraceRecorder gradeResultTraceRecorder = (courseId, calculationBatchId) -> { };

    public GradeRecordService(
            GradeItemRepository gradeItemRepository,
            GradeRecordRepository gradeRecordRepository,
            GradeChangeLogRepository gradeChangeLogRepository,
            GradePublishRecordRepository gradePublishRecordRepository,
            CourseGradeSummaryRepository courseGradeSummaryRepository,
            GradeCalculationBatchRepository gradeCalculationBatchRepository,
            SourceGradeClient sourceGradeClient,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.gradeItemRepository = gradeItemRepository;
        this.gradeRecordRepository = gradeRecordRepository;
        this.gradeChangeLogRepository = gradeChangeLogRepository;
        this.gradePublishRecordRepository = gradePublishRecordRepository;
        this.courseGradeSummaryRepository = courseGradeSummaryRepository;
        this.gradeCalculationBatchRepository = gradeCalculationBatchRepository;
        this.sourceGradeClient = sourceGradeClient;
        this.coursePermissionClient = coursePermissionClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Autowired(required = false)
    public void setGradeResultTraceRecorder(GradeResultTraceRecorder gradeResultTraceRecorder) {
        this.gradeResultTraceRecorder = gradeResultTraceRecorder;
    }

    @Transactional
    public GradeSyncResult syncSourceGrades(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        List<GradeItem> sourceItems = sourceGradeItems(courseId);
        LocalDateTime now = LocalDateTime.now();
        int syncedCount = 0;
        int missingCount = 0;
        int ungradedCount = 0;
        Set<Long> studentIds = courseStudentIds(courseId);
        Map<GradeItem, Map<Long, SourceGradeDTO>> sourceGradesByItem = new LinkedHashMap<>();
        Map<String, GradeRecord> existingRecordsByStudentAndItem = gradeRecordRepository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(record -> studentItemKey(record.studentId(), record.gradeItemId()), record -> record));

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
                GradeRecord existingRecord = existingRecordsByStudentAndItem.get(studentItemKey(studentId, item.id()));
                record = preservePublishedState(record, existingRecord);
                gradeRecordRepository.upsert(record);
                recordSourceResyncChange(existingRecord, record, teacherId, now);
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
        gradeResultTraceRecorder.record(courseId, calculationBatchId);
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

    @Transactional
    public GradePublishResult publishCourseGrades(long courseId, long teacherId, PublishCourseGradesCommand command) {
        requireCoursePermission(courseId, teacherId);
        List<GradeItem> includedItems = includedGradeItems(courseId);
        if (includedItems.isEmpty()) {
            throw new InvalidGradeRuleException("成绩规则缺失，不能发布课程成绩");
        }
        String publishScope = normalizePublishScope(command);
        Set<Long> targetStudentIds = normalizePublishStudentIds(courseId, command);
        Set<Long> targetGradeItemIds = normalizePublishGradeItemIds(command, includedItems, publishScope);
        String idempotencyKey = publishIdempotencyKey(courseId, publishScope, targetStudentIds, targetGradeItemIds);
        java.util.Optional<GradePublishRecord> existingPublishRecord =
                gradePublishRecordRepository.findByIdempotencyKey(courseId, idempotencyKey);
        Map<Long, List<GradeRecord>> recordsByStudent = gradeRecordRepository.findByCourseId(courseId).stream()
                .filter(record -> targetStudentIds.contains(record.studentId()))
                .collect(Collectors.groupingBy(GradeRecord::studentId));
        Map<Long, CourseGradeSummary> summariesByStudent = courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .filter(summary -> targetStudentIds.contains(summary.studentId()))
                .collect(Collectors.toMap(CourseGradeSummary::studentId, summary -> summary));

        for (long studentId : targetStudentIds) {
            CourseGradeSummary summary = summariesByStudent.get(studentId);
            if (summary == null || summary.finalStatus() == FinalStatus.INCOMPLETE || summary.finalScore() == null) {
                throw new GradePublishException("仍存在未评分或缺失成绩，不能发布");
            }
            List<GradeRecord> records = recordsByStudent.getOrDefault(studentId, List.of()).stream()
                    .filter(record -> targetGradeItemIds.contains(record.gradeItemId()))
                    .toList();
            if (records.size() < targetGradeItemIds.size() || records.stream().anyMatch(this::notPublishableRecord)) {
                throw new GradePublishException("仍存在未评分或缺失成绩，不能发布");
            }
        }

        if (existingPublishRecord.isPresent() && allTargetRowsPublished(targetStudentIds, targetGradeItemIds, recordsByStudent, summariesByStudent)) {
            GradePublishRecord record = existingPublishRecord.get();
            return new GradePublishResult(
                    record.id(),
                    record.publishedCount(),
                    record.publishedAt(),
                    record.notificationStatus()
            );
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> recipientStudentIds = targetStudentIds.stream()
                .filter(studentId -> !isStudentFullyPublished(studentId, targetGradeItemIds, recordsByStudent, summariesByStudent))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (long studentId : targetStudentIds) {
            List<GradeRecord> records = recordsByStudent.getOrDefault(studentId, List.of()).stream()
                    .filter(record -> targetGradeItemIds.contains(record.gradeItemId()))
                    .toList();
            for (GradeRecord record : records) {
                gradeRecordRepository.update(record.published(now));
            }
            courseGradeSummaryRepository.update(summariesByStudent.get(studentId).published(now));
        }

        GradePublishRecord publishRecord = gradePublishRecordRepository.save(new GradePublishRecord(
                0L,
                courseId,
                idempotencyKey,
                publishScope,
                recipientStudentIds.size(),
                teacherId,
                now,
                "SENT",
                publishRemark(targetStudentIds, targetGradeItemIds)
        ));
        if (!recipientStudentIds.isEmpty()) {
            notificationEventPublisher.publish(new NotificationEvent(
                    "GRD:GRADE_PUBLISHED:PUBLISH:" + publishRecord.id(),
                    "GRADE_PUBLISHED",
                    courseId,
                    List.copyOf(recipientStudentIds),
                    "成绩已发布",
                    "课程成绩已发布，请查看成绩明细。",
                    "GRADE_PUBLISH_RECORD",
                    publishRecord.id(),
                    "/courses/" + courseId + "?page=grades",
                    now
            ));
        }
        return new GradePublishResult(
                publishRecord.id(),
                publishRecord.publishedCount(),
                publishRecord.publishedAt(),
                publishRecord.notificationStatus()
        );
    }

    public GradePublishRecordPage listGradePublishRecords(long courseId, long teacherId, int page, int size) {
        requireCoursePermission(courseId, teacherId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        return new GradePublishRecordPage(
                gradePublishRecordRepository.findByCourseId(courseId, normalizedPage, normalizedSize),
                gradePublishRecordRepository.countByCourseId(courseId),
                normalizedPage,
                normalizedSize
        );
    }

    public CourseGradeRow getMyPublishedGrades(long courseId, long studentId) {
        if (!coursePermissionClient.isCourseMember(courseId, studentId)) {
            throw new StudentGradeAccessException("学生无课程成绩访问权限");
        }
        CourseGradeSummary summary = courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .filter(item -> item.studentId() == studentId)
                .filter(item -> item.publishStatus() == PublishStatus.PUBLISHED)
                .findFirst()
                .orElseThrow(() -> new GradePublishException("成绩未发布，不能查看未公开成绩"));
        List<GradeRecord> records = gradeRecordRepository.findByCourseId(courseId).stream()
                .filter(record -> record.studentId() == studentId)
                .filter(record -> record.publishStatus() == PublishStatus.PUBLISHED)
                .sorted(Comparator.comparingLong(GradeRecord::gradeItemId))
                .toList();
        return new CourseGradeRow(studentId, summary, records);
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

    private boolean notPublishableRecord(GradeRecord record) {
        return record.gradeStatus() != GradeStatus.SCORED && record.gradeStatus() != GradeStatus.ADJUSTED;
    }

    private boolean allTargetRowsPublished(
            Set<Long> targetStudentIds,
            Set<Long> targetGradeItemIds,
            Map<Long, List<GradeRecord>> recordsByStudent,
            Map<Long, CourseGradeSummary> summariesByStudent
    ) {
        return targetStudentIds.stream()
                .allMatch(studentId -> isStudentFullyPublished(studentId, targetGradeItemIds, recordsByStudent, summariesByStudent));
    }

    private boolean isStudentFullyPublished(
            long studentId,
            Set<Long> targetGradeItemIds,
            Map<Long, List<GradeRecord>> recordsByStudent,
            Map<Long, CourseGradeSummary> summariesByStudent
    ) {
        CourseGradeSummary summary = summariesByStudent.get(studentId);
        List<GradeRecord> records = recordsByStudent.getOrDefault(studentId, List.of()).stream()
                .filter(record -> targetGradeItemIds.contains(record.gradeItemId()))
                .toList();
        return summary != null
                && summary.publishStatus() == PublishStatus.PUBLISHED
                && !records.isEmpty()
                && records.stream().allMatch(record -> record.publishStatus() == PublishStatus.PUBLISHED);
    }

    private Set<Long> normalizePublishStudentIds(long courseId, PublishCourseGradesCommand command) {
        String scope = normalizePublishScope(command);
        Set<Long> requestedStudentIds = command.studentIds() == null
                ? Set.of()
                : command.studentIds().stream()
                .filter(studentId -> studentId != null && studentId > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> targetStudentIds;
        if ("PARTIAL_STUDENTS".equals(scope)) {
            targetStudentIds = new LinkedHashSet<>(requestedStudentIds);
        } else {
            targetStudentIds = studentIdsForCalculation(courseId);
        }
        if (targetStudentIds.isEmpty()) {
            throw new GradePublishException("发布范围为空，不能发布课程成绩");
        }
        Set<Long> courseStudentIds = courseStudentIds(courseId);
        if (!courseStudentIds.isEmpty() && targetStudentIds.stream().anyMatch(studentId -> !courseStudentIds.contains(studentId))) {
            throw new GradePublishException("发布范围包含非课程成员");
        }
        return targetStudentIds;
    }

    private Set<Long> normalizePublishGradeItemIds(
            PublishCourseGradesCommand command,
            List<GradeItem> includedItems,
            String publishScope
    ) {
        Set<Long> includedItemIds = includedItems.stream()
                .map(GradeItem::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if ("PARTIAL_ITEMS".equals(publishScope)) {
            throw new GradePublishException("部分成绩项发布暂未实现，不能提前公开课程总评");
        }
        if (command.gradeItemIds() == null || command.gradeItemIds().isEmpty()) {
            return includedItemIds;
        }
        throw new GradePublishException("部分成绩项发布暂未实现，不能提前公开课程总评");
    }

    private String normalizePublishScope(PublishCourseGradesCommand command) {
        String scope = command.publishScope() == null ? "COURSE" : command.publishScope().trim().toUpperCase();
        if (scope.isEmpty()) {
            return "COURSE";
        }
        if (!"COURSE".equals(scope) && !"PARTIAL_STUDENTS".equals(scope) && !"PARTIAL_ITEMS".equals(scope)) {
            throw new GradePublishException("成绩发布范围不合法");
        }
        return scope;
    }

    private String publishRemark(Set<Long> studentIds, Set<Long> gradeItemIds) {
        return "students=" + studentIds.size() + ";gradeItems=" + gradeItemIds.size();
    }

    private String publishIdempotencyKey(
            long courseId,
            String publishScope,
            Set<Long> studentIds,
            Set<Long> gradeItemIds
    ) {
        String rawKey = courseId + "|" + publishScope + "|students="
                + studentIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","))
                + "|gradeItems="
                + gradeItemIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
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

    private String studentItemKey(long studentId, long gradeItemId) {
        return studentId + "|" + gradeItemId;
    }

    private GradeRecord preservePublishedState(GradeRecord record, GradeRecord existingRecord) {
        if (existingRecord == null || existingRecord.publishStatus() != PublishStatus.PUBLISHED) {
            return record;
        }
        return new GradeRecord(
                record.id(),
                record.courseId(),
                record.studentId(),
                record.gradeItemId(),
                record.sourceType(),
                record.sourceId(),
                record.rawScore(),
                record.weightedScore(),
                record.gradeStatus(),
                PublishStatus.PUBLISHED,
                record.comment(),
                record.sourceUpdatedAt(),
                record.calculatedAt(),
                existingRecord.publishedAt(),
                existingRecord.createdAt(),
                record.updatedAt()
        );
    }

    private void recordSourceResyncChange(
            GradeRecord existingRecord,
            GradeRecord syncedRecord,
            long operatorId,
            LocalDateTime changedAt
    ) {
        if (existingRecord == null
                || existingRecord.publishStatus() != PublishStatus.PUBLISHED
                || !sourceGradeChanged(existingRecord, syncedRecord)) {
            return;
        }
        GradeChangeLog changeLog = gradeChangeLogRepository.save(new GradeChangeLog(
                0L,
                syncedRecord.courseId(),
                syncedRecord.studentId(),
                syncedRecord.gradeItemId(),
                "SOURCE_RESYNC",
                existingRecord.rawScore(),
                syncedRecord.rawScore(),
                "来源成绩重新同步刷新已发布成绩",
                operatorId,
                changedAt
        ));
        publishGradeChangedEvent(changeLog, "GRADE_ITEM", syncedRecord.gradeItemId(), changedAt);
    }

    private boolean sourceGradeChanged(GradeRecord existingRecord, GradeRecord syncedRecord) {
        return compareScore(existingRecord.rawScore(), syncedRecord.rawScore()) != 0
                || compareScore(existingRecord.weightedScore(), syncedRecord.weightedScore()) != 0
                || existingRecord.gradeStatus() != syncedRecord.gradeStatus();
    }

    private int compareScore(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null || right == null) {
            return left == null ? -1 : 1;
        }
        return left.compareTo(right);
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
