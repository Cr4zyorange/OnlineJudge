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
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeReviewServiceTest {
    @Test
    void studentSubmitsPublishedItemReviewAndNotifiesCourseTeachers() {
        InMemoryGradeReviewRequestRepository reviewRepository = new InMemoryGradeReviewRequestRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        RecordingNotificationEventPublisher eventPublisher = new RecordingNotificationEventPublisher();
        GradeReviewService service = new GradeReviewService(
                reviewRepository,
                new InMemoryGradeItemRepository(),
                recordRepository,
                summaryRepository,
                new InMemoryGradeChangeLogRepository(),
                permissionClient(List.of(601L), List.of(501L)),
                eventPublisher
        );
        LocalDateTime publishedAt = LocalDateTime.now().minusHours(2);
        recordRepository.upsert(record(101L, 601L, 11L, "90.00", PublishStatus.PUBLISHED, publishedAt));

        GradeReviewSubmissionResult result = service.submitReviewRequest(
                101L,
                601L,
                new SubmitGradeReviewCommand(11L, GradeReviewTargetType.ITEM_SCORE, "实验报告评分漏看附录")
        );

        assertThat(result.status()).isEqualTo(GradeReviewStatus.PENDING);
        assertThat(result.requestId()).isPositive();
        assertThat(reviewRepository.findById(result.requestId())).contains(reviewRepository.requests.get(0));
        assertThat(reviewRepository.requests.get(0)).satisfies(request -> {
            assertThat(request.courseId()).isEqualTo(101L);
            assertThat(request.studentId()).isEqualTo(601L);
            assertThat(request.gradeItemId()).isEqualTo(11L);
            assertThat(request.targetType()).isEqualTo(GradeReviewTargetType.ITEM_SCORE);
            assertThat(request.originalScore()).isEqualByComparingTo("90.00");
            assertThat(request.reason()).isEqualTo("实验报告评分漏看附录");
        });
        assertThat(eventPublisher.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("GRADE_REVIEW_REQUESTED");
            assertThat(event.courseId()).isEqualTo(101L);
            assertThat(event.recipientUserIds()).containsExactly(501L);
            assertThat(event.targetType()).isEqualTo("GRADE_REVIEW_REQUEST");
            assertThat(event.targetId()).isEqualTo(result.requestId());
        });
    }

    @Test
    void studentCannotSubmitDuplicatePendingReviewForSameTarget() {
        InMemoryGradeReviewRequestRepository reviewRepository = new InMemoryGradeReviewRequestRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        GradeReviewService service = new GradeReviewService(
                reviewRepository,
                new InMemoryGradeItemRepository(),
                recordRepository,
                new InMemoryCourseGradeSummaryRepository(),
                new InMemoryGradeChangeLogRepository(),
                permissionClient(List.of(601L), List.of(501L)),
                new RecordingNotificationEventPublisher()
        );
        recordRepository.upsert(record(101L, 601L, 11L, "90.00", PublishStatus.PUBLISHED, LocalDateTime.now()));
        service.submitReviewRequest(101L, 601L, new SubmitGradeReviewCommand(11L, GradeReviewTargetType.ITEM_SCORE, "第一次申请"));

        assertThatThrownBy(() -> service.submitReviewRequest(
                101L,
                601L,
                new SubmitGradeReviewCommand(11L, GradeReviewTargetType.ITEM_SCORE, "重复申请")
        ))
                .isInstanceOf(GradeReviewDuplicateException.class)
                .hasMessageContaining("已有处理中成绩异议申请");
    }

    @Test
    void teacherApprovesFinalScoreReviewWithTraceAndStudentNotification() {
        InMemoryGradeReviewRequestRepository reviewRepository = new InMemoryGradeReviewRequestRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        InMemoryGradeChangeLogRepository changeLogRepository = new InMemoryGradeChangeLogRepository();
        RecordingNotificationEventPublisher eventPublisher = new RecordingNotificationEventPublisher();
        GradeReviewService service = new GradeReviewService(
                reviewRepository,
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                summaryRepository,
                changeLogRepository,
                permissionClient(List.of(601L), List.of(501L)),
                eventPublisher
        );
        CourseGradeSummary summary = summaryRepository.upsert(summary(101L, 601L, "84.00", PublishStatus.PUBLISHED));
        GradeReviewRequest request = reviewRepository.save(new GradeReviewRequest(
                0L,
                101L,
                601L,
                null,
                GradeReviewTargetType.FINAL_SCORE,
                "总评未计入补交成绩",
                GradeReviewStatus.PENDING,
                new BigDecimal("84.00"),
                null,
                null,
                LocalDateTime.now().minusHours(1),
                null,
                null,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().minusHours(1)
        ));

        GradeReviewProcessResult result = service.processReviewRequest(
                request.id(),
                501L,
                new ProcessGradeReviewCommand("APPROVE", new BigDecimal("88.00"), "确认补交成绩有效")
        );

        assertThat(result.status()).isEqualTo(GradeReviewStatus.APPROVED);
        assertThat(summaryRepository.findById(summary.id()).orElseThrow().finalScore()).isEqualByComparingTo("88.00");
        assertThat(reviewRepository.findById(request.id()).orElseThrow()).satisfies(processed -> {
            assertThat(processed.status()).isEqualTo(GradeReviewStatus.APPROVED);
            assertThat(processed.adjustedScore()).isEqualByComparingTo("88.00");
            assertThat(processed.responseComment()).isEqualTo("确认补交成绩有效");
            assertThat(processed.processedBy()).isEqualTo(501L);
            assertThat(processed.processedAt()).isNotNull();
        });
        assertThat(changeLogRepository.logs).singleElement().satisfies(log -> {
            assertThat(log.changeType()).isEqualTo("FINAL_ADJUST");
            assertThat(log.oldValue()).isEqualByComparingTo("84.00");
            assertThat(log.newValue()).isEqualByComparingTo("88.00");
            assertThat(log.reason()).contains("确认补交成绩有效");
        });
        assertThat(eventPublisher.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("GRADE_REVIEW_PROCESSED");
            assertThat(event.recipientUserIds()).containsExactly(601L);
            assertThat(event.targetId()).isEqualTo(request.id());
        });
    }

    private static CoursePermissionClient permissionClient(List<Long> studentIds, List<Long> teacherIds) {
        return new CoursePermissionClient() {
            @Override
            public boolean canManageCourse(long courseId, long userId) {
                return teacherIds.contains(userId);
            }

            @Override
            public boolean isCourseMember(long courseId, long userId) {
                return studentIds.contains(userId) || teacherIds.contains(userId);
            }

            @Override
            public List<Long> listCourseStudentIds(long courseId) {
                return studentIds;
            }

            @Override
            public List<Long> listCourseTeacherIds(long courseId) {
                return teacherIds;
            }
        };
    }

    private static GradeRecord record(long courseId, long studentId, long gradeItemId, String rawScore, PublishStatus publishStatus, LocalDateTime publishedAt) {
        LocalDateTime now = LocalDateTime.now();
        return new GradeRecord(
                0L,
                courseId,
                studentId,
                gradeItemId,
                SourceType.LAB,
                301L,
                new BigDecimal(rawScore),
                new BigDecimal(rawScore),
                GradeStatus.SCORED,
                publishStatus,
                null,
                now,
                now,
                publishedAt,
                now,
                now
        );
    }

    private static CourseGradeSummary summary(long courseId, long studentId, String finalScore, PublishStatus publishStatus) {
        LocalDateTime now = LocalDateTime.now();
        return new CourseGradeSummary(
                0L,
                courseId,
                studentId,
                new BigDecimal(finalScore),
                FinalStatus.CALCULATED,
                publishStatus,
                1L,
                now.minusHours(2),
                now,
                now
        );
    }

    private static final class RecordingNotificationEventPublisher implements NotificationEventPublisher {
        private final List<NotificationEvent> events = new ArrayList<>();

        @Override
        public void publish(NotificationEvent event) {
            events.add(event);
        }

        List<NotificationEvent> events() {
            return events;
        }
    }

    private static final class InMemoryGradeReviewRequestRepository implements GradeReviewRequestRepository {
        private long nextId = 1L;
        private final List<GradeReviewRequest> requests = new ArrayList<>();

        @Override
        public GradeReviewRequest save(GradeReviewRequest request) {
            GradeReviewRequest saved = request.withId(nextId++);
            requests.add(saved);
            return saved;
        }

        @Override
        public GradeReviewRequest update(GradeReviewRequest request) {
            for (int index = 0; index < requests.size(); index++) {
                if (requests.get(index).id() == request.id()) {
                    requests.set(index, request);
                    return request;
                }
            }
            throw new IllegalArgumentException("review request not found");
        }

        @Override
        public Optional<GradeReviewRequest> findById(long id) {
            return requests.stream().filter(request -> request.id() == id).findFirst();
        }

        @Override
        public Optional<GradeReviewRequest> findPendingByTarget(long courseId, long studentId, GradeReviewTargetType targetType, Long gradeItemId) {
            return requests.stream()
                    .filter(request -> request.courseId() == courseId)
                    .filter(request -> request.studentId() == studentId)
                    .filter(request -> request.targetType() == targetType)
                    .filter(request -> gradeItemId == null ? request.gradeItemId() == null : gradeItemId.equals(request.gradeItemId()))
                    .filter(request -> request.status() == GradeReviewStatus.PENDING)
                    .findFirst();
        }

        @Override
        public List<GradeReviewRequest> findByCourseId(long courseId, Long studentId, Long gradeItemId, GradeReviewStatus status, int page, int size) {
            return requests.stream().filter(request -> request.courseId() == courseId).toList();
        }

        @Override
        public int countByCourseId(long courseId, Long studentId, Long gradeItemId, GradeReviewStatus status) {
            return findByCourseId(courseId, studentId, gradeItemId, status, 1, Integer.MAX_VALUE).size();
        }
    }

    private static final class InMemoryGradeItemRepository implements GradeItemRepository {
        @Override
        public GradeItem save(GradeItem item) {
            return item;
        }

        @Override
        public GradeItem update(GradeItem item) {
            return item;
        }

        @Override
        public Optional<GradeItem> findById(long id) {
            LocalDateTime now = LocalDateTime.now();
            return Optional.of(new GradeItem(
                    id,
                    101L,
                    "实验一",
                    SourceType.LAB,
                    301L,
                    new BigDecimal("100.00"),
                    BigDecimal.ONE,
                    true,
                    true,
                    1,
                    501L,
                    false,
                    now,
                    now
            ));
        }

        @Override
        public List<GradeItem> findByCourseId(long courseId) {
            return List.of();
        }
    }

    private static final class InMemoryGradeRecordRepository implements GradeRecordRepository {
        private long nextId = 1L;
        private final List<GradeRecord> records = new ArrayList<>();

        @Override
        public GradeRecord upsert(GradeRecord record) {
            GradeRecord saved = record.withId(nextId++);
            records.add(saved);
            return saved;
        }

        @Override
        public GradeRecord update(GradeRecord record) {
            for (int index = 0; index < records.size(); index++) {
                if (records.get(index).id() == record.id()) {
                    records.set(index, record);
                    return record;
                }
            }
            throw new IllegalArgumentException("record not found");
        }

        @Override
        public Optional<GradeRecord> findById(long id) {
            return records.stream().filter(record -> record.id() == id).findFirst();
        }

        @Override
        public List<GradeRecord> findByCourseId(long courseId) {
            return records.stream().filter(record -> record.courseId() == courseId).toList();
        }
    }

    private static final class InMemoryCourseGradeSummaryRepository implements CourseGradeSummaryRepository {
        private long nextId = 1L;
        private final List<CourseGradeSummary> summaries = new ArrayList<>();

        @Override
        public CourseGradeSummary upsert(CourseGradeSummary summary) {
            CourseGradeSummary saved = summary.withId(nextId++);
            summaries.add(saved);
            return saved;
        }

        @Override
        public CourseGradeSummary update(CourseGradeSummary summary) {
            for (int index = 0; index < summaries.size(); index++) {
                if (summaries.get(index).id() == summary.id()) {
                    summaries.set(index, summary);
                    return summary;
                }
            }
            throw new IllegalArgumentException("summary not found");
        }

        @Override
        public Optional<CourseGradeSummary> findById(long id) {
            return summaries.stream().filter(summary -> summary.id() == id).findFirst();
        }

        @Override
        public List<CourseGradeSummary> findByCourseId(long courseId) {
            return summaries.stream().filter(summary -> summary.courseId() == courseId).toList();
        }
    }

    private static final class InMemoryGradeChangeLogRepository implements GradeChangeLogRepository {
        private long nextId = 1L;
        private final List<GradeChangeLog> logs = new ArrayList<>();

        @Override
        public GradeChangeLog save(GradeChangeLog log) {
            GradeChangeLog saved = log.withId(nextId++);
            logs.add(saved);
            return saved;
        }

        @Override
        public List<GradeChangeLog> findByCourseId(long courseId, Long studentId, Long gradeItemId, int page, int size) {
            return logs.stream().filter(log -> log.courseId() == courseId).toList();
        }

        @Override
        public int countByCourseId(long courseId, Long studentId, Long gradeItemId) {
            return findByCourseId(courseId, studentId, gradeItemId, 1, Integer.MAX_VALUE).size();
        }
    }
}
