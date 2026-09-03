package com.onlinejudge.courseservice.learning;

import com.onlinejudge.courseservice.persistence.CourseRepository;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Course-owned learning progress facts (LRN folded into Course, #355). */
@Service
public class LrnProgressService {
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LrnProgressRepository progress;
    private final LrnTaskService tasks;
    private final CourseRepository courses;

    public LrnProgressService(LrnProgressRepository progress, LrnTaskService tasks, CourseRepository courses) {
        this.progress = progress;
        this.tasks = tasks;
        this.courses = courses;
    }

    public LearningProgressOverview overview(long userId, Long courseId) {
        List<LrnProgressRepository.ProgressRow> rows = progress.listByUser(userId, courseId);
        Map<Long, List<LrnProgressRepository.ProgressRow>> byCourse =
                rows.stream().collect(Collectors.groupingBy(LrnProgressRepository.ProgressRow::courseId, LinkedHashMap::new, Collectors.toList()));
        List<LearningCourseProgress> courses = byCourse.entrySet().stream()
                .map(entry -> courseProgress(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(LearningCourseProgress::courseId))
                .toList();
        return new LearningProgressOverview(courses, courses.size());
    }

    public LearningCourseProgressAggregate teacherOverview(CurrentUser actor, long courseId) {
        if (!actor.hasRole("TEACHER") && !actor.hasRole("ADMIN")) {
            throw new CourseException(HttpStatus.FORBIDDEN, "LEARNING_PROGRESS_FORBIDDEN", "无权查看课程学习进度统计", false);
        }
        CourseRepository.Course course = courses.findCourse(courseId)
                .orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "course does not exist", false));
        List<LrnProgressRepository.ProgressRow> rows = progress.listByCourse(courseId);
        Map<Long, List<LrnProgressRepository.ProgressRow>> byStudent =
                rows.stream().collect(Collectors.groupingBy(LrnProgressRepository.ProgressRow::userId));
        List<LearningStudentProgressSummary> students = byStudent.entrySet().stream()
                .map(entry -> {
                    int percent = entry.getValue().stream().mapToInt(LrnProgressRepository.ProgressRow::progressPercent).max().orElse(0);
                    return new LearningStudentProgressSummary(entry.getKey(), "学生" + entry.getKey(), percent,
                            status(percent), format(latest(entry.getValue()).updatedAt()));
                })
                .sorted(Comparator.comparingLong(LearningStudentProgressSummary::studentId))
                .toList();
        int average = students.isEmpty() ? 0
                : (int) Math.round(students.stream().mapToInt(LearningStudentProgressSummary::progressPercent).average().orElse(0));
        return new LearningCourseProgressAggregate(courseId, course.name(), students.size(), average, students);
    }

    @Transactional
    public LearningProgressItem save(long userId, LearningProgressSaveRequest request) {
        if (request == null || request.courseId() == null || request.sourceModule() == null || request.sourceId() == null) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "LEARNING_PROGRESS_INVALID", "学习进度参数不合法", false);
        }
        int percent = request.progressPercent() == null ? 0 : Math.max(0, Math.min(100, request.progressPercent()));
        String status = percent >= 100 ? "COMPLETED" : (percent > 0 ? "IN_PROGRESS" : "NOT_STARTED");
        progress.upsert(userId, request.courseId(), request.chapterId(), request.sourceModule(), request.sourceId(),
                percent, request.lastPosition(), status);
        tasks.reflectProgress(userId, request.courseId(), request.sourceModule(), request.sourceId(), percent);
        return overview(userId, request.courseId()).courses().stream()
                .flatMap(course -> course.chapters().stream().flatMap(chapter -> chapter.records().stream()))
                .filter(item -> item.sourceModule().equals(request.sourceModule()) && item.sourceId() == request.sourceId())
                .findFirst().orElseThrow();
    }

    private LearningCourseProgress courseProgress(long courseId, List<LrnProgressRepository.ProgressRow> rows) {
        Map<Long, List<LrnProgressRepository.ProgressRow>> byChapter =
                rows.stream().collect(Collectors.groupingBy(row -> row.chapterId() == null ? 0L : row.chapterId(),
                        LinkedHashMap::new, Collectors.toList()));
        List<LearningChapterProgress> chapters = byChapter.entrySet().stream()
                .map(entry -> {
                    Long chapterId = entry.getKey() == 0 ? null : entry.getKey();
                    String chapterName = entry.getValue().getFirst().chapterName();
                    List<LearningProgressItem> records = entry.getValue().stream()
                            .map(this::item).sorted(Comparator.comparingLong(LearningProgressItem::sourceId)).toList();
                    int percent = records.stream().mapToInt(LearningProgressItem::progressPercent).max().orElse(0);
                    LrnProgressRepository.ProgressRow latest = latest(entry.getValue());
                    return new LearningChapterProgress(chapterId, chapterName, percent, status(percent),
                            latest.lastPosition(), continueUrl(latest), format(latest.updatedAt()), records);
                })
                .sorted(Comparator.comparing(chapter -> chapter.chapterId() == null ? -1L : chapter.chapterId()))
                .toList();
        int percent = rows.stream().mapToInt(LrnProgressRepository.ProgressRow::progressPercent).max().orElse(0);
        LrnProgressRepository.ProgressRow latest = latest(rows);
        LearningProgressItem continueLearning = rows.stream()
                .max(Comparator.comparing(LrnProgressRepository.ProgressRow::updatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(this::item).orElse(null);
        return new LearningCourseProgress(courseId, latest.courseName(), percent, status(percent), latest.lastPosition(),
                continueLearning == null ? null : continueLearning.continueUrl(), format(latest.updatedAt()), continueLearning, chapters);
    }

    private LearningProgressItem item(LrnProgressRepository.ProgressRow row) {
        return new LearningProgressItem(row.id(), row.courseId(), row.courseName(), row.chapterId(), row.chapterName(),
                row.sourceModule(), row.sourceId(), row.progressPercent(), row.lastPosition(), status(row.progressPercent()),
                continueUrl(row), format(row.updatedAt()));
    }

    private String continueUrl(LrnProgressRepository.ProgressRow row) {
        return switch (row.sourceModule()) {
            case "LAB" -> "/courses/" + row.courseId() + "/labs/" + row.sourceId();
            case "HWK" -> "/courses/" + row.courseId() + "/homeworks/" + row.sourceId();
            default -> "/courses/" + row.courseId();
        };
    }

    private LrnProgressRepository.ProgressRow latest(List<LrnProgressRepository.ProgressRow> rows) {
        return rows.stream().max(Comparator.comparing(LrnProgressRepository.ProgressRow::updatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()))).orElseThrow();
    }

    private String status(int percent) {
        return percent >= 100 ? "COMPLETED" : (percent > 0 ? "IN_PROGRESS" : "NOT_STARTED");
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? null : value.format(RESPONSE_TIME_FORMAT);
    }

    public record LearningProgressOverview(List<LearningCourseProgress> courses, int total) { }
    public record LearningCourseProgress(long courseId, String courseName, int progressPercent, String status,
                                         String lastPosition, String continueUrl, String updatedAt,
                                         LearningProgressItem continueLearning, List<LearningChapterProgress> chapters) { }
    public record LearningChapterProgress(Long chapterId, String chapterName, int progressPercent, String status,
                                          String lastPosition, String continueUrl, String updatedAt,
                                          List<LearningProgressItem> records) { }
    public record LearningProgressItem(long progressId, long courseId, String courseName, Long chapterId, String chapterName,
                                       String sourceModule, long sourceId, int progressPercent, String lastPosition,
                                       String status, String continueUrl, String updatedAt) { }
    public record LearningProgressSaveRequest(Long courseId, Long chapterId, String sourceModule, Long sourceId,
                                              Integer progressPercent, String lastPosition) { }
    public record LearningCourseProgressAggregate(long courseId, String courseName, int studentCount,
                                                  int averageProgressPercent, List<LearningStudentProgressSummary> students) { }
    public record LearningStudentProgressSummary(long studentId, String studentName, int progressPercent,
                                                 String status, String updatedAt) { }
}
