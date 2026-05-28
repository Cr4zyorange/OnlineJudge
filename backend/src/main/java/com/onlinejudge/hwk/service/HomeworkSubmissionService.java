package com.onlinejudge.hwk.service;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkEvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmitType;
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
    private final HomeworkEvaluationService homeworkEvaluationService;

    public HomeworkSubmissionService(
            HomeworkRepository homeworkRepository,
            HomeworkSubmissionRepository submissionRepository,
            CoursePermissionClient coursePermissionClient,
            HomeworkEvaluationService homeworkEvaluationService
    ) {
        this.homeworkRepository = homeworkRepository;
        this.submissionRepository = submissionRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.homeworkEvaluationService = homeworkEvaluationService;
    }

    @Transactional
    public HomeworkSubmission submit(long homeworkId, SubmitHomeworkCommand command, CurrentUser currentUser) {
        requireStudent(currentUser);
        Homework homework = findExisting(homeworkId);
        requireCourseMember(homework, currentUser.id());
        validateSubmittable(homework, currentUser.id());
        SubmissionContent content = validateContent(homework.type(), command);
        LocalDateTime now = LocalDateTime.now();
        HomeworkSubmitStatus status = now.isAfter(homework.deadline())
                ? HomeworkSubmitStatus.LATE
                : HomeworkSubmitStatus.SUBMITTED;
        HomeworkSubmission submission = new HomeworkSubmission(
                0L,
                homework.id(),
                currentUser.id(),
                content.submitType(),
                content.answerText(),
                content.answerJson(),
                content.fileUrl(),
                content.language(),
                status,
                HomeworkEvaluationStatus.NONE,
                HomeworkReviewStatus.UNREVIEWED,
                null,
                null,
                null,
                null,
                true,
                true,
                now,
                null,
                null,
                now,
                now
        );
        HomeworkSubmission saved = submissionRepository.save(submission);
        homeworkEvaluationService.evaluate(homework, saved);
        return submissionRepository.findById(saved.id()).orElse(saved);
    }

    public List<HomeworkSubmission> listMine(long homeworkId, CurrentUser currentUser) {
        requireStudent(currentUser);
        Homework homework = findExisting(homeworkId);
        requireCourseMember(homework, currentUser.id());
        if (homework.status() == HomeworkStatus.DRAFT) {
            throw new ApiException("ERR-HWK-01", "作业尚未发布", HttpStatus.FORBIDDEN);
        }
        return submissionRepository.findByHomeworkIdAndStudentId(homeworkId, currentUser.id());
    }

    public List<HomeworkSubmission> listForTeacher(long homeworkId, CurrentUser currentUser) {
        Homework homework = findExisting(homeworkId);
        requireManagePermission(homework.courseId(), currentUser.id());
        return submissionRepository.findByHomeworkId(homeworkId);
    }

    public HomeworkSubmission get(long submissionId, CurrentUser currentUser) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ApiException("ERR-HWK-07", "提交记录不存在", HttpStatus.NOT_FOUND));
        Homework homework = findExisting(submission.homeworkId());
        if (submission.studentId() == currentUser.id() && coursePermissionClient.isCourseMember(homework.courseId(), currentUser.id())) {
            return submission;
        }
        requireManagePermission(homework.courseId(), currentUser.id());
        return submission;
    }

    private Homework findExisting(long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(homework -> !homework.deleted())
                .orElseThrow(() -> new ApiException("ERR-HWK-04", "作业不存在", HttpStatus.NOT_FOUND));
    }

    private void requireStudent(CurrentUser currentUser) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("学生才能提交作业");
        }
    }

    private void requireCourseMember(Homework homework, long userId) {
        if (!coursePermissionClient.isCourseMember(homework.courseId(), userId)) {
            throw new ApiException("ERR-HWK-01", "无课程作业访问权限", HttpStatus.FORBIDDEN);
        }
    }

    private void requireManagePermission(long courseId, long userId) {
        if (!coursePermissionClient.canManageCourse(courseId, userId)) {
            throw new ApiException("ERR-HWK-01", "无课程作业管理权限", HttpStatus.FORBIDDEN);
        }
    }

    private void validateSubmittable(Homework homework, long studentId) {
        if (homework.status() != HomeworkStatus.PUBLISHED) {
            throw new ApiException("ERR-HWK-02", "当前作业状态不允许提交", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(homework.deadline()) && !homework.allowLateSubmit()) {
            throw new ApiException("ERR-HWK-06", "作业已截止，不允许逾期提交", HttpStatus.BAD_REQUEST);
        }
        if (!homework.allowResubmit()
                && submissionRepository.findLatestByHomeworkIdAndStudentId(homework.id(), studentId).isPresent()) {
            throw new ApiException("ERR-HWK-05", "该作业不允许重复提交", HttpStatus.BAD_REQUEST);
        }
    }

    private SubmissionContent validateContent(HomeworkType homeworkType, SubmitHomeworkCommand command) {
        return switch (homeworkType) {
            case OBJECTIVE -> {
                if (isBlank(command.answerJson())) {
                    throw new ApiException("ERR-HWK-03", "客观题答案不能为空", HttpStatus.BAD_REQUEST);
                }
                yield new SubmissionContent(HomeworkSubmitType.OBJECTIVE, null, command.answerJson().trim(), null, null);
            }
            case CODE -> {
                if (isBlank(command.codeText())) {
                    throw new ApiException("ERR-HWK-03", "代码内容不能为空", HttpStatus.BAD_REQUEST);
                }
                if (isBlank(command.language())) {
                    throw new ApiException("ERR-HWK-03", "代码语言不能为空", HttpStatus.BAD_REQUEST);
                }
                yield new SubmissionContent(
                        HomeworkSubmitType.CODE,
                        command.codeText(),
                        null,
                        null,
                        command.language().trim()
                );
            }
            case FILE -> {
                if (isBlank(command.fileUrl()) && isBlank(command.answerText())) {
                    throw new ApiException("ERR-HWK-03", "文件作业需提交文本或附件", HttpStatus.BAD_REQUEST);
                }
                HomeworkSubmitType submitType = isBlank(command.fileUrl()) ? HomeworkSubmitType.TEXT : HomeworkSubmitType.FILE;
                yield new SubmissionContent(
                        submitType,
                        trimToNull(command.answerText()),
                        null,
                        trimToNull(command.fileUrl()),
                        null
                );
            }
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record SubmissionContent(
            HomeworkSubmitType submitType,
            String answerText,
            String answerJson,
            String fileUrl,
            String language
    ) {
    }
}
