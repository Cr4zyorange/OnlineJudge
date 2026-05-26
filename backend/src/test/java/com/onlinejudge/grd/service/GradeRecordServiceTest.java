package com.onlinejudge.grd.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.grd.domain.CourseGradeSummary;
import com.onlinejudge.grd.domain.CourseGradeSummaryRepository;
import com.onlinejudge.grd.domain.GradeChangeLog;
import com.onlinejudge.grd.domain.GradeChangeLogRepository;
import com.onlinejudge.grd.domain.GradeCalculationBatch;
import com.onlinejudge.grd.domain.GradeCalculationBatchRepository;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.GradeRecord;
import com.onlinejudge.grd.domain.GradeRecordRepository;
import com.onlinejudge.grd.domain.GradeStatus;
import com.onlinejudge.grd.domain.FinalStatus;
import com.onlinejudge.grd.domain.PublishStatus;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeRecordServiceTest {
    @Test
    void teacherSyncsLabAndHomeworkSourceGradesThenCalculatesFinalScores() {
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        InMemoryGradeCalculationBatchRepository batchRepository = new InMemoryGradeCalculationBatchRepository();
        GradeRecordService service = new GradeRecordService(
                itemRepository,
                recordRepository,
                new InMemoryGradeChangeLogRepository(),
                summaryRepository,
                batchRepository,
                sourceGradesForCourse101(),
                permissionClient(601L, 602L, 603L),
                new RecordingNotificationEventPublisher()
        );
        itemRepository.add(item(1L, 101L, "实验一", SourceType.LAB, 301L, "100.00", "0.40"));
        itemRepository.add(item(2L, 101L, "作业一", SourceType.HWK, 401L, "100.00", "0.60"));

        GradeSyncResult result = service.syncSourceGrades(101L, 501L);

        assertThat(result.calculationBatchId()).isPositive();
        assertThat(result.syncedCount()).isEqualTo(3);
        assertThat(result.ungradedCount()).isEqualTo(1);
        assertThat(result.missingCount()).isEqualTo(2);
        assertThat(result.affectedStudentCount()).isEqualTo(3);
        assertThat(batchRepository.findById(result.calculationBatchId()).orElseThrow().triggerType()).isEqualTo("SYNC");

        GradeRecord labScore = recordRepository.findByStudentAndItem(101L, 601L, 1L).orElseThrow();
        assertThat(labScore.rawScore()).isEqualByComparingTo("90.00");
        assertThat(labScore.weightedScore()).isEqualByComparingTo("36.00");
        assertThat(labScore.gradeStatus()).isEqualTo(GradeStatus.SCORED);
        assertThat(labScore.publishStatus()).isEqualTo(PublishStatus.UNPUBLISHED);

        GradeRecord ungradedHomework = recordRepository.findByStudentAndItem(101L, 602L, 2L).orElseThrow();
        assertThat(ungradedHomework.rawScore()).isNull();
        assertThat(ungradedHomework.weightedScore()).isNull();
        assertThat(ungradedHomework.gradeStatus()).isEqualTo(GradeStatus.UNGRADED);

        CourseGradeSummary completed = summaryRepository.findByStudent(101L, 601L).orElseThrow();
        assertThat(completed.finalScore()).isEqualByComparingTo("84.00");
        assertThat(completed.finalStatus()).isEqualTo(FinalStatus.CALCULATED);

        CourseGradeSummary incomplete = summaryRepository.findByStudent(101L, 602L).orElseThrow();
        assertThat(incomplete.finalScore()).isNull();
        assertThat(incomplete.finalStatus()).isEqualTo(FinalStatus.INCOMPLETE);

        GradeRecord missingLab = recordRepository.findByStudentAndItem(101L, 603L, 1L).orElseThrow();
        GradeRecord missingHomework = recordRepository.findByStudentAndItem(101L, 603L, 2L).orElseThrow();
        assertThat(missingLab.gradeStatus()).isEqualTo(GradeStatus.MISSING);
        assertThat(missingHomework.gradeStatus()).isEqualTo(GradeStatus.MISSING);
        CourseGradeSummary missingSummary = summaryRepository.findByStudent(101L, 603L).orElseThrow();
        assertThat(missingSummary.finalScore()).isNull();
        assertThat(missingSummary.finalStatus()).isEqualTo(FinalStatus.INCOMPLETE);
    }

    @Test
    void teacherCannotSyncGradesWithoutCoursePermission() {
        GradeRecordService service = new GradeRecordService(
                new InMemoryGradeItemRepository(),
                new InMemoryGradeRecordRepository(),
                new InMemoryGradeChangeLogRepository(),
                new InMemoryCourseGradeSummaryRepository(),
                new InMemoryGradeCalculationBatchRepository(),
                (courseId, sourceType, sourceId) -> List.of(),
                (courseId, userId) -> false,
                new RecordingNotificationEventPublisher()
        );

        assertThatThrownBy(() -> service.syncSourceGrades(101L, 501L))
                .isInstanceOf(GradeItemPermissionException.class)
                .hasMessageContaining("教师无课程成绩管理权限");
    }

    @Test
    void teacherAdjustsPublishedGradeRecordAndNotifiesStudentWithoutUnpublishingSummary() {
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        RecordingNotificationEventPublisher eventPublisher = new RecordingNotificationEventPublisher();
        GradeRecordService service = new GradeRecordService(
                itemRepository,
                recordRepository,
                new InMemoryGradeChangeLogRepository(),
                summaryRepository,
                new InMemoryGradeCalculationBatchRepository(),
                (courseId, sourceType, sourceId) -> List.of(),
                permissionClient(601L),
                eventPublisher
        );
        itemRepository.add(item(11L, 101L, "实验一", SourceType.LAB, 301L, "100.00", "1.00"));
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(1);
        GradeRecord record = recordRepository.upsert(record(101L, 601L, 11L, "80.00", "80.00", PublishStatus.PUBLISHED, publishedAt));
        CourseGradeSummary summary = summaryRepository.upsert(summary(101L, 601L, "80.00", PublishStatus.PUBLISHED, publishedAt));

        GradeAdjustmentResult result = service.adjustGradeRecord(
                record.id(),
                501L,
                new AdjustGradeRecordCommand(new BigDecimal("95.00"), "已发布成绩复核")
        );

        assertThat(result.newScore()).isEqualByComparingTo("95.00");
        CourseGradeSummary recalculatedSummary = summaryRepository.findById(summary.id()).orElseThrow();
        assertThat(recalculatedSummary.finalScore()).isEqualByComparingTo("95.00");
        assertThat(recalculatedSummary.publishStatus()).isEqualTo(PublishStatus.PUBLISHED);
        assertThat(recalculatedSummary.publishedAt()).isEqualTo(publishedAt);
        assertThat(eventPublisher.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("GRADE_CHANGED");
            assertThat(event.courseId()).isEqualTo(101L);
            assertThat(event.recipientUserIds()).containsExactly(601L);
            assertThat(event.targetType()).isEqualTo("GRADE_ITEM");
            assertThat(event.targetId()).isEqualTo(11L);
            assertThat(event.occurredAt()).isNotNull();
        });
    }

    @Test
    void teacherAdjustsUnpublishedGradeRecordWithoutNotifyingStudent() {
        InMemoryGradeItemRepository itemRepository = new InMemoryGradeItemRepository();
        InMemoryGradeRecordRepository recordRepository = new InMemoryGradeRecordRepository();
        InMemoryCourseGradeSummaryRepository summaryRepository = new InMemoryCourseGradeSummaryRepository();
        RecordingNotificationEventPublisher eventPublisher = new RecordingNotificationEventPublisher();
        GradeRecordService service = new GradeRecordService(
                itemRepository,
                recordRepository,
                new InMemoryGradeChangeLogRepository(),
                summaryRepository,
                new InMemoryGradeCalculationBatchRepository(),
                (courseId, sourceType, sourceId) -> List.of(),
                permissionClient(601L),
                eventPublisher
        );
        itemRepository.add(item(1L, 101L, "实验一", SourceType.LAB, 301L, "100.00", "1.00"));
        GradeRecord record = recordRepository.upsert(record(101L, 601L, 1L, "80.00", "80.00", PublishStatus.UNPUBLISHED, null));

        service.adjustGradeRecord(
                record.id(),
                501L,
                new AdjustGradeRecordCommand(new BigDecimal("95.00"), "未发布成绩复核")
        );

        assertThat(eventPublisher.events()).isEmpty();
    }

    private CoursePermissionClient permissionClient(Long... studentIds) {
        return new CoursePermissionClient() {
            @Override
            public boolean canManageCourse(long courseId, long userId) {
                return true;
            }

            @Override
            public List<Long> listCourseStudentIds(long courseId) {
                return List.of(studentIds);
            }
        };
    }

    private SourceGradeClient sourceGradesForCourse101() {
        return (courseId, sourceType, sourceId) -> {
            if (courseId != 101L) {
                return List.of();
            }
            if (sourceType == SourceGradeType.LAB && sourceId == 301L) {
                return List.of(
                        source(courseId, sourceType, sourceId, 601L, "90.00", "100.00", "SCORED"),
                        source(courseId, sourceType, sourceId, 602L, "78.00", "100.00", "SCORED")
                );
            }
            if (sourceType == SourceGradeType.HWK && sourceId == 401L) {
                return List.of(
                        source(courseId, sourceType, sourceId, 601L, "80.00", "100.00", "SCORED"),
                        source(courseId, sourceType, sourceId, 602L, null, "100.00", "UNGRADED")
                );
            }
            return List.of();
        };
    }

    private SourceGradeDTO source(
            long courseId,
            SourceGradeType sourceType,
            long sourceId,
            long studentId,
            String score,
            String fullScore,
            String status
    ) {
        return new SourceGradeDTO(
                courseId,
                sourceType,
                sourceId,
                studentId,
                score == null ? null : new BigDecimal(score),
                new BigDecimal(fullScore),
                status,
                LocalDateTime.now()
        );
    }

    private GradeItem item(
            long id,
            long courseId,
            String name,
            SourceType sourceType,
            long sourceId,
            String fullScore,
            String weight
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new GradeItem(
                id,
                courseId,
                name,
                sourceType,
                sourceId,
                new BigDecimal(fullScore),
                new BigDecimal(weight),
                true,
                true,
                (int) id,
                501L,
                false,
                now,
                now
        );
    }

    private GradeRecord record(
            long courseId,
            long studentId,
            long gradeItemId,
            String rawScore,
            String weightedScore,
            PublishStatus publishStatus,
            LocalDateTime publishedAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new GradeRecord(
                0L,
                courseId,
                studentId,
                gradeItemId,
                SourceType.LAB,
                301L,
                new BigDecimal(rawScore),
                new BigDecimal(weightedScore),
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

    private CourseGradeSummary summary(
            long courseId,
            long studentId,
            String finalScore,
            PublishStatus publishStatus,
            LocalDateTime publishedAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new CourseGradeSummary(
                0L,
                courseId,
                studentId,
                new BigDecimal(finalScore),
                FinalStatus.CALCULATED,
                publishStatus,
                1L,
                publishedAt,
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

    private static final class InMemoryGradeItemRepository implements GradeItemRepository {
        private final List<GradeItem> items = new ArrayList<>();

        void add(GradeItem item) {
            items.add(item);
        }

        @Override
        public GradeItem save(GradeItem item) {
            items.add(item);
            return item;
        }

        @Override
        public GradeItem update(GradeItem item) {
            return item;
        }

        @Override
        public Optional<GradeItem> findById(long id) {
            return items.stream().filter(item -> item.id() == id).findFirst();
        }

        @Override
        public List<GradeItem> findByCourseId(long courseId) {
            return items.stream().filter(item -> item.courseId() == courseId && item.enabled()).toList();
        }
    }

    private static final class InMemoryGradeRecordRepository implements GradeRecordRepository {
        private long nextId = 1L;
        private final List<GradeRecord> records = new ArrayList<>();

        @Override
        public GradeRecord upsert(GradeRecord record) {
            for (int index = 0; index < records.size(); index++) {
                GradeRecord existing = records.get(index);
                if (existing.courseId() == record.courseId()
                        && existing.studentId() == record.studentId()
                        && existing.gradeItemId() == record.gradeItemId()) {
                    GradeRecord updated = record.withId(existing.id());
                    records.set(index, updated);
                    return updated;
                }
            }
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

        Optional<GradeRecord> findByStudentAndItem(long courseId, long studentId, long gradeItemId) {
            return records.stream()
                    .filter(record -> record.courseId() == courseId)
                    .filter(record -> record.studentId() == studentId)
                    .filter(record -> record.gradeItemId() == gradeItemId)
                    .findFirst();
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
            return logs.stream()
                    .filter(log -> log.courseId() == courseId)
                    .filter(log -> studentId == null || log.studentId() == studentId)
                    .filter(log -> gradeItemId == null || gradeItemId.equals(log.gradeItemId()))
                    .toList();
        }

        @Override
        public int countByCourseId(long courseId, Long studentId, Long gradeItemId) {
            return findByCourseId(courseId, studentId, gradeItemId, 1, Integer.MAX_VALUE).size();
        }
    }

    private static final class InMemoryCourseGradeSummaryRepository implements CourseGradeSummaryRepository {
        private long nextId = 1L;
        private final List<CourseGradeSummary> summaries = new ArrayList<>();

        @Override
        public CourseGradeSummary upsert(CourseGradeSummary summary) {
            for (int index = 0; index < summaries.size(); index++) {
                CourseGradeSummary existing = summaries.get(index);
                if (existing.courseId() == summary.courseId() && existing.studentId() == summary.studentId()) {
                    CourseGradeSummary updated = summary.withId(existing.id());
                    summaries.set(index, updated);
                    return updated;
                }
            }
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

        Optional<CourseGradeSummary> findByStudent(long courseId, long studentId) {
            return summaries.stream()
                    .filter(summary -> summary.courseId() == courseId)
                    .filter(summary -> summary.studentId() == studentId)
                    .findFirst();
        }
    }

    private static final class InMemoryGradeCalculationBatchRepository implements GradeCalculationBatchRepository {
        private long nextId = 1L;
        private final List<GradeCalculationBatch> batches = new ArrayList<>();

        @Override
        public GradeCalculationBatch save(GradeCalculationBatch batch) {
            GradeCalculationBatch saved = batch.withId(nextId++);
            batches.add(saved);
            return saved;
        }

        Optional<GradeCalculationBatch> findById(long id) {
            return batches.stream()
                    .filter(batch -> batch.id() == id)
                    .findFirst();
        }
    }
}
