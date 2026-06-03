package com.onlinejudge.grd.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.GradeChangeLog;
import com.onlinejudge.grd.domain.GradeChangeLogRepository;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeRecordRepository;
import com.onlinejudge.grd.domain.GradeReviewRequest;
import com.onlinejudge.grd.domain.GradeReviewRequestRepository;
import com.onlinejudge.grd.domain.GradeReviewStatus;
import com.onlinejudge.grd.domain.GradeReviewTargetType;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GradeReviewService {
    private final GradeReviewRequestRepository reviewRequestRepository;
    private final GradeItemRepository gradeItemRepository;
    private final GradeRecordRepository gradeRecordRepository;
    private final CourseGradeSummaryRepository courseGradeSummaryRepository;
    private final GradeChangeLogRepository gradeChangeLogRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public GradeReviewService(
            GradeReviewRequestRepository reviewRequestRepository,
            GradeItemRepository gradeItemRepository,
            GradeRecordRepository gradeRecordRepository,
            CourseGradeSummaryRepository courseGradeSummaryRepository,
            GradeChangeLogRepository gradeChangeLogRepository,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.reviewRequestRepository = reviewRequestRepository;
        this.gradeItemRepository = gradeItemRepository;
        this.gradeRecordRepository = gradeRecordRepository;
        this.courseGradeSummaryRepository = courseGradeSummaryRepository;
        this.gradeChangeLogRepository = gradeChangeLogRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional
    public GradeReviewSubmissionResult submitReviewRequest(long courseId, long studentId, SubmitGradeReviewCommand command) {
        if (!coursePermissionClient.isCourseMember(courseId, studentId)) {
            throw new GradeReviewPermissionException("学生无课程成绩复核权限");
        }
        GradeReviewTargetType targetType = requireTargetType(command.targetType());
        Long gradeItemId = normalizeGradeItemId(targetType, command.gradeItemId());
        String reason = normalizeText(command.reason(), "异议申请理由不能为空");
        BigDecimal originalScore = originalPublishedScore(courseId, studentId, targetType, gradeItemId);
        reviewRequestRepository.findPendingByTarget(courseId, studentId, targetType, gradeItemId)
                .ifPresent(existing -> {
                    throw new GradeReviewDuplicateException("已有处理中成绩异议申请");
                });

        LocalDateTime now = LocalDateTime.now();
        GradeReviewRequest saved = reviewRequestRepository.save(new GradeReviewRequest(
                0L,
                courseId,
                studentId,
                gradeItemId,
                targetType,
                reason,
                GradeReviewStatus.PENDING,
                originalScore,
                null,
                null,
                now,
                null,
                null,
                now,
                now
        ));
        notificationEventPublisher.publish(new NotificationEvent(
                "GRD:GRADE_REVIEW_REQUESTED:REQUEST:" + saved.id(),
                "GRADE_REVIEW_REQUESTED",
                courseId,
                coursePermissionClient.listCourseTeacherIds(courseId),
                "收到成绩复核申请",
                "学生提交了成绩异议申请，请及时处理。",
                "GRADE_REVIEW_REQUEST",
                saved.id(),
                "/courses/" + courseId + "?page=grades&reviewId=" + saved.id(),
                now
        ));
        return new GradeReviewSubmissionResult(saved.id(), saved.status(), saved.submittedAt());
    }

    public GradeReviewRequestPage listMyReviewRequests(long courseId, long studentId, GradeReviewStatus status, int page, int size) {
        if (!coursePermissionClient.isCourseMember(courseId, studentId)) {
            throw new GradeReviewPermissionException("学生无课程成绩复核权限");
        }
        return listRequests(courseId, studentId, null, status, page, size);
    }

    public GradeReviewRequestPage listCourseReviewRequests(
            long courseId,
            long teacherId,
            Long studentId,
            Long gradeItemId,
            GradeReviewStatus status,
            int page,
            int size
    ) {
        requireTeacherPermission(courseId, teacherId);
        return listRequests(courseId, studentId, gradeItemId, status, page, size);
    }

    @Transactional
    public GradeReviewProcessResult processReviewRequest(long requestId, long teacherId, ProcessGradeReviewCommand command) {
        GradeReviewRequest request = reviewRequestRepository.findById(requestId)
                .orElseThrow(() -> new GradeItemNotFoundException("成绩异议申请不存在"));
        requireTeacherPermission(request.courseId(), teacherId);
        if (request.status() != GradeReviewStatus.PENDING) {
            throw new GradeReviewValidationException("成绩异议申请已处理，不能重复处理");
        }
        String responseComment = normalizeText(command.responseComment(), "复核处理说明不能为空");
        String action = command.action() == null ? "" : command.action().trim().toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        GradeReviewRequest processed;
        if ("REJECT".equals(action) || "REJECTED".equals(action)) {
            processed = reviewRequestRepository.update(request.processed(
                    GradeReviewStatus.REJECTED,
                    null,
                    responseComment,
                    teacherId,
                    now
            ));
        } else if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
            BigDecimal adjustedScore = applyApprovedAdjustment(request, command.adjustedScore(), responseComment, teacherId, now);
            processed = reviewRequestRepository.update(request.processed(
                    GradeReviewStatus.APPROVED,
                    adjustedScore,
                    responseComment,
                    teacherId,
                    now
            ));
        } else {
            throw new GradeReviewValidationException("复核处理动作不合法");
        }

        notificationEventPublisher.publish(new NotificationEvent(
                "GRD:GRADE_REVIEW_PROCESSED:REQUEST:" + processed.id(),
                "GRADE_REVIEW_PROCESSED",
                processed.courseId(),
                List.of(processed.studentId()),
                "成绩复核已处理",
                "成绩异议申请已处理，请查看复核结果。",
                "GRADE_REVIEW_REQUEST",
                processed.id(),
                "/courses/" + processed.courseId() + "?page=grades",
                now
        ));
        return new GradeReviewProcessResult(processed.id(), processed.status(), processed.processedAt());
    }

    private GradeReviewRequestPage listRequests(
            long courseId,
            Long studentId,
            Long gradeItemId,
            GradeReviewStatus status,
            int page,
            int size
    ) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        return new GradeReviewRequestPage(
                reviewRequestRepository.findByCourseId(courseId, studentId, gradeItemId, status, normalizedPage, normalizedSize)
                        .stream()
                        .map(GradeReviewRequestView::from)
                        .toList(),
                reviewRequestRepository.countByCourseId(courseId, studentId, gradeItemId, status),
                normalizedPage,
                normalizedSize
        );
    }

    private BigDecimal applyApprovedAdjustment(
            GradeReviewRequest request,
            BigDecimal adjustedScore,
            String responseComment,
            long teacherId,
            LocalDateTime now
    ) {
        if (request.targetType() == GradeReviewTargetType.FINAL_SCORE) {
            BigDecimal normalizedScore = normalizeScore(adjustedScore, new BigDecimal("100.00"));
            CourseGradeSummary summary = publishedSummary(request.courseId(), request.studentId());
            courseGradeSummaryRepository.update(summary.adjusted(normalizedScore, now));
            gradeChangeLogRepository.save(new GradeChangeLog(
                    0L,
                    request.courseId(),
                    request.studentId(),
                    null,
                    "FINAL_ADJUST",
                    summary.finalScore(),
                    normalizedScore,
                    responseComment,
                    teacherId,
                    now
            ));
            return normalizedScore;
        }

        GradeRecord record = publishedRecord(request.courseId(), request.studentId(), request.gradeItemId());
        GradeItem gradeItem = gradeItemRepository.findById(record.gradeItemId())
                .orElseThrow(() -> new GradeItemNotFoundException("成绩项不存在"));
        BigDecimal normalizedScore = normalizeScore(adjustedScore, gradeItem.fullScore());
        BigDecimal weightedScore = normalizedScore.multiply(gradeItem.weight()).setScale(2, RoundingMode.HALF_UP);
        gradeRecordRepository.update(record.adjusted(normalizedScore, weightedScore, now));
        gradeChangeLogRepository.save(new GradeChangeLog(
                0L,
                request.courseId(),
                request.studentId(),
                record.gradeItemId(),
                "RECORD_ADJUST",
                record.rawScore(),
                normalizedScore,
                responseComment,
                teacherId,
                now
        ));
        recalculateStudentSummary(request.courseId(), request.studentId(), now);
        return normalizedScore;
    }

    private void recalculateStudentSummary(long courseId, long studentId, LocalDateTime now) {
        Map<Long, GradeItem> includedItems = gradeItemRepository.findByCourseId(courseId).stream()
                .filter(GradeItem::enabled)
                .filter(GradeItem::includedInFinal)
                .collect(Collectors.toMap(GradeItem::id, item -> item));
        List<GradeRecord> includedRecords = gradeRecordRepository.findByCourseId(courseId).stream()
                .filter(record -> record.studentId() == studentId)
                .filter(record -> includedItems.containsKey(record.gradeItemId()))
                .sorted(Comparator.comparingLong(GradeRecord::gradeItemId))
                .toList();
        if (includedItems.isEmpty() || includedRecords.size() < includedItems.size()) {
            return;
        }
        boolean complete = includedRecords.stream()
                .allMatch(record -> record.gradeStatus() == GradeStatus.SCORED || record.gradeStatus() == GradeStatus.ADJUSTED);
        if (!complete) {
            return;
        }
        BigDecimal finalScore = includedRecords.stream()
                .map(GradeRecord::weightedScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .filter(summary -> summary.studentId() == studentId)
                .findFirst()
                .ifPresent(summary -> courseGradeSummaryRepository.update(new CourseGradeSummary(
                        summary.id(),
                        summary.courseId(),
                        summary.studentId(),
                        finalScore,
                        FinalStatus.ADJUSTED,
                        summary.publishStatus(),
                        summary.calculationBatchId(),
                        summary.publishedAt(),
                        summary.createdAt(),
                        now
                )));
    }

    private BigDecimal originalPublishedScore(long courseId, long studentId, GradeReviewTargetType targetType, Long gradeItemId) {
        if (targetType == GradeReviewTargetType.FINAL_SCORE) {
            return publishedSummary(courseId, studentId).finalScore();
        }
        return publishedRecord(courseId, studentId, gradeItemId).rawScore();
    }

    private CourseGradeSummary publishedSummary(long courseId, long studentId) {
        return courseGradeSummaryRepository.findByCourseId(courseId).stream()
                .filter(summary -> summary.studentId() == studentId)
                .filter(summary -> summary.publishStatus() == PublishStatus.PUBLISHED)
                .findFirst()
                .orElseThrow(() -> new GradeReviewValidationException("只能对本人已发布成绩提交异议"));
    }

    private GradeRecord publishedRecord(long courseId, long studentId, Long gradeItemId) {
        if (gradeItemId == null) {
            throw new GradeReviewValidationException("单项成绩异议必须指定成绩项");
        }
        return gradeRecordRepository.findByCourseId(courseId).stream()
                .filter(record -> record.studentId() == studentId)
                .filter(record -> record.gradeItemId() == gradeItemId)
                .filter(record -> record.publishStatus() == PublishStatus.PUBLISHED)
                .findFirst()
                .orElseThrow(() -> new GradeReviewValidationException("只能对本人已发布成绩提交异议"));
    }

    private void requireTeacherPermission(long courseId, long teacherId) {
        if (!coursePermissionClient.canManageCourseGrade(courseId, teacherId)) {
            throw new GradeReviewPermissionException("教师无权限处理课程成绩异议申请");
        }
    }

    private GradeReviewTargetType requireTargetType(GradeReviewTargetType targetType) {
        if (targetType == null) {
            throw new GradeReviewValidationException("复核目标类型不能为空");
        }
        return targetType;
    }

    private Long normalizeGradeItemId(GradeReviewTargetType targetType, Long gradeItemId) {
        if (targetType == GradeReviewTargetType.FINAL_SCORE) {
            return null;
        }
        if (gradeItemId == null || gradeItemId <= 0) {
            throw new GradeReviewValidationException("单项成绩异议必须指定成绩项");
        }
        return gradeItemId;
    }

    private String normalizeText(String text, String emptyMessage) {
        if (text == null || text.trim().isEmpty()) {
            throw new GradeReviewValidationException(emptyMessage);
        }
        String normalized = text.trim();
        if (normalized.length() > 1000) {
            throw new GradeReviewValidationException("成绩异议说明不能超过 1000 个字符");
        }
        return normalized;
    }

    private BigDecimal normalizeScore(BigDecimal score, BigDecimal fullScore) {
        if (score == null) {
            throw new GradeReviewValidationException("同意修改必须填写调整后成绩");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(fullScore) > 0) {
            throw new GradeReviewValidationException("调整后成绩必须在 0 到满分之间");
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }
}
