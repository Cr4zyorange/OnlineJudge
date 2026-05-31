package com.onlinejudge.hwk.service;

import com.onlinejudge.common.web.PageResponse;
import com.onlinejudge.hwk.controller.HomeworkSubmissionRequest;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HomeworkSubmissionService {
    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final CoursePermissionClient coursePermissionClient;

    public HomeworkSubmissionService(
            HomeworkRepository homeworkRepository,
            HomeworkSubmissionRepository submissionRepository,
            CoursePermissionClient coursePermissionClient
    ) {
        this.homeworkRepository = homeworkRepository;
        this.submissionRepository = submissionRepository;
        this.coursePermissionClient = coursePermissionClient;
    }

    @Transactional
    public HomeworkSubmission submit(long homeworkId, long studentId, HomeworkSubmissionRequest request) {
        HomeworkSubmissionRequest normalizedRequest = request == null
                ? new HomeworkSubmissionRequest(null, null, null, null, null)
                : request;
        Homework homework = findExistingHomework(homeworkId);
        requireStudentMember(homework.courseId(), studentId);
        requireSubmittable(homework);
        if (submissionRepository.findFinalByHomeworkAndStudent(homeworkId, studentId).isPresent()
                && !homework.allowResubmit()) {
            throw new HomeworkApiException("HWK_4095", "resubmit is not allowed", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        String submitStatus = now.isAfter(homework.deadline()) ? "LATE" : "SUBMITTED";
        String answerText = answerText(homework.type(), normalizedRequest);
        String answerJson = answerJson(homework.type(), normalizedRequest);
        String fileUrl = fileUrl(homework.type(), normalizedRequest);
        String language = language(homework.type(), normalizedRequest);
        validateContent(homework.type(), answerText, answerJson, fileUrl, language);

        submissionRepository.clearFinalSubmission(homeworkId, studentId);
        return submissionRepository.save(new HomeworkSubmission(
                0L,
                homeworkId,
                studentId,
                homework.type().name(),
                answerText,
                answerJson,
                fileUrl,
                language,
                submitStatus,
                initialEvaluationStatus(homework.type()),
                "UNREVIEWED",
                null,
                null,
                null,
                null,
                true,
                now,
                null,
                null,
                now,
                now
        ));
    }

    public List<HomeworkSubmission> listMine(long homeworkId, long studentId) {
        Homework homework = findExistingHomework(homeworkId);
        requireStudentMember(homework.courseId(), studentId);
        if (homework.status() != HomeworkStatus.PUBLISHED
                && homework.status() != HomeworkStatus.CLOSED
                && homework.status() != HomeworkStatus.SCORE_PUBLISHED
                && homework.status() != HomeworkStatus.ARCHIVED) {
            throw new HomeworkApiException("HWK_4002", "homework is not published", HttpStatus.FORBIDDEN);
        }
        return submissionRepository.findByHomeworkAndStudent(homeworkId, studentId);
    }

    public PageResponse<HomeworkSubmission> listForManager(
            long homeworkId,
            long managerId,
            Long studentId,
            String submitStatus,
            String evaluationStatus,
            String reviewStatus,
            int page,
            int size
    ) {
        Homework homework = findExistingHomework(homeworkId);
        requireCourseManager(homework.courseId(), managerId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<HomeworkSubmission> submissions = submissionRepository.findByHomework(
                homeworkId,
                studentId,
                blankToNull(submitStatus),
                blankToNull(evaluationStatus),
                blankToNull(reviewStatus),
                normalizedPage,
                normalizedSize
        );
        long total = submissionRepository.countByHomework(
                homeworkId,
                studentId,
                blankToNull(submitStatus),
                blankToNull(evaluationStatus),
                blankToNull(reviewStatus)
        );
        return new PageResponse<>(submissions, total, normalizedPage, normalizedSize);
    }

    public HomeworkSubmission detailForStudent(long submissionId, long studentId) {
        HomeworkSubmission submission = findExistingSubmission(submissionId);
        Homework homework = findExistingHomework(submission.homeworkId());
        requireStudentMember(homework.courseId(), studentId);
        if (submission.studentId() != studentId) {
            throw new HomeworkApiException("HWK_4031", "submission access denied", HttpStatus.FORBIDDEN);
        }
        return submission;
    }

    public HomeworkSubmission detailForManager(long submissionId, long managerId) {
        HomeworkSubmission submission = findExistingSubmission(submissionId);
        Homework homework = findExistingHomework(submission.homeworkId());
        requireCourseManager(homework.courseId(), managerId);
        return submission;
    }

    public boolean isLatest(HomeworkSubmission submission) {
        return submissionRepository.findByHomeworkAndStudent(submission.homeworkId(), submission.studentId())
                .stream()
                .findFirst()
                .map(latest -> latest.id() == submission.id())
                .orElse(false);
    }

    private Homework findExistingHomework(long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(homework -> !homework.deleted())
                .orElseThrow(() -> new HomeworkApiException("HWK_4001", "homework not found", HttpStatus.NOT_FOUND));
    }

    private HomeworkSubmission findExistingSubmission(long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new HomeworkApiException("HWK_4009", "submission not found", HttpStatus.NOT_FOUND));
    }

    private void requireStudentMember(long courseId, long studentId) {
        if (!coursePermissionClient.isCourseMember(courseId, studentId)) {
            throw new HomeworkApiException("HWK_4031", "course access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireCourseManager(long courseId, long userId) {
        if (!coursePermissionClient.canManageCourse(courseId, userId)) {
            throw new HomeworkApiException("HWK_4031", "course management permission required", HttpStatus.FORBIDDEN);
        }
    }

    private void requireSubmittable(Homework homework) {
        if (homework.status() != HomeworkStatus.PUBLISHED) {
            throw new HomeworkApiException("HWK_4093", "homework is not open for submission", HttpStatus.CONFLICT);
        }
        if (LocalDateTime.now().isAfter(homework.deadline()) && !homework.allowLateSubmit()) {
            throw new HomeworkApiException("HWK_4094", "homework deadline has passed", HttpStatus.CONFLICT);
        }
    }

    private void validateContent(HomeworkType type, String answerText, String answerJson, String fileUrl, String language) {
        boolean valid = switch (type) {
            case TEXT -> answerText != null && !answerText.isBlank();
            case FILE -> fileUrl != null && !fileUrl.isBlank();
            case CODE -> answerText != null && !answerText.isBlank() && language != null && !language.isBlank();
            case OBJECTIVE -> answerJson != null && !answerJson.isBlank();
        };
        if (!valid) {
            throw new HomeworkApiException("HWK_4008", "submission content does not match homework type", HttpStatus.BAD_REQUEST);
        }
    }

    private String answerText(HomeworkType type, HomeworkSubmissionRequest request) {
        if (type == HomeworkType.CODE) {
            return blankToNull(request.codeText());
        }
        if (type == HomeworkType.TEXT) {
            return blankToNull(request.answerText());
        }
        return null;
    }

    private String answerJson(HomeworkType type, HomeworkSubmissionRequest request) {
        return type == HomeworkType.OBJECTIVE ? blankToNull(request.answerJson()) : null;
    }

    private String fileUrl(HomeworkType type, HomeworkSubmissionRequest request) {
        return type == HomeworkType.FILE ? blankToNull(request.fileUrl()) : null;
    }

    private String language(HomeworkType type, HomeworkSubmissionRequest request) {
        return type == HomeworkType.CODE ? blankToNull(request.language()) : null;
    }

    private String initialEvaluationStatus(HomeworkType type) {
        if (type == HomeworkType.CODE) {
            return "PENDING";
        }
        if (type == HomeworkType.OBJECTIVE) {
            return "NOT_EVALUATED";
        }
        return "NOT_REQUIRED";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
