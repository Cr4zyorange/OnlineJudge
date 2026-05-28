package com.onlinejudge.hwk.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class HomeworkService {
    private final HomeworkRepository homeworkRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public HomeworkService(
            HomeworkRepository homeworkRepository,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.homeworkRepository = homeworkRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional
    public Homework create(CreateHomeworkCommand command, CurrentUser currentUser) {
        requireTeacher(currentUser);
        requireManagePermission(command.courseId(), currentUser.id());
        validate(command);
        LocalDateTime now = LocalDateTime.now();
        Homework homework = new Homework(
                0L,
                command.courseId(),
                command.chapterId(),
                command.title().trim(),
                command.description().trim(),
                command.type(),
                HomeworkStatus.DRAFT,
                command.totalScore(),
                command.deadline(),
                command.allowResubmit(),
                command.allowLateSubmit(),
                command.showEvaluationBeforePublish(),
                null,
                currentUser.id(),
                null,
                false,
                now,
                now,
                command.questions(),
                command.testCases()
        );
        return homeworkRepository.save(homework);
    }

    public Homework get(long homeworkId, CurrentUser currentUser) {
        Homework homework = findExisting(homeworkId);
        if (!coursePermissionClient.canViewCourse(homework.courseId(), currentUser.id())) {
            throw new ApiException("ERR-HWK-01", "无课程作业访问权限", HttpStatus.FORBIDDEN);
        }
        if (!coursePermissionClient.canManageCourse(homework.courseId(), currentUser.id())
                && homework.status() == HomeworkStatus.DRAFT) {
            throw new ApiException("ERR-HWK-01", "作业尚未发布", HttpStatus.FORBIDDEN);
        }
        return homework;
    }

    public List<Homework> list(Long courseId, CurrentUser currentUser) {
        if (courseId == null || courseId <= 0) {
            return List.of();
        }
        if (!coursePermissionClient.canViewCourse(courseId, currentUser.id())) {
            throw new ApiException("ERR-HWK-01", "无课程作业访问权限", HttpStatus.FORBIDDEN);
        }
        boolean canManage = coursePermissionClient.canManageCourse(courseId, currentUser.id());
        return homeworkRepository.findByCourseId(courseId).stream()
                .filter(homework -> canManage || homework.status() != HomeworkStatus.DRAFT)
                .toList();
    }

    @Transactional
    public Homework update(long homeworkId, CreateHomeworkCommand command, CurrentUser currentUser) {
        requireTeacher(currentUser);
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), currentUser.id());
        if (existing.status() != HomeworkStatus.DRAFT) {
            throw new ApiException("ERR-HWK-02", "仅草稿作业允许修改", HttpStatus.BAD_REQUEST);
        }
        if (existing.courseId() != command.courseId()) {
            throw new ApiException("ERR-HWK-03", "不允许修改作业所属课程", HttpStatus.BAD_REQUEST);
        }
        validate(command);
        Homework updated = new Homework(
                existing.id(),
                existing.courseId(),
                command.chapterId(),
                command.title().trim(),
                command.description().trim(),
                command.type(),
                existing.status(),
                command.totalScore(),
                command.deadline(),
                command.allowResubmit(),
                command.allowLateSubmit(),
                command.showEvaluationBeforePublish(),
                existing.judgeConfigId(),
                existing.createdBy(),
                existing.publishedAt(),
                existing.deleted(),
                existing.createdAt(),
                LocalDateTime.now(),
                command.questions(),
                command.testCases()
        );
        return homeworkRepository.update(updated);
    }

    @Transactional
    public Homework publish(long homeworkId, CurrentUser currentUser) {
        requireTeacher(currentUser);
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), currentUser.id());
        if (existing.status() != HomeworkStatus.DRAFT) {
            throw new ApiException("ERR-HWK-02", "当前作业状态不允许发布", HttpStatus.BAD_REQUEST);
        }
        validatePublishReady(existing);
        Homework published = homeworkRepository.update(existing.publish(LocalDateTime.now()));
        List<Long> studentIds = coursePermissionClient.listCourseStudentIds(published.courseId());
        notificationEventPublisher.publish(new NotificationEvent(
                "homework-published-" + published.id() + "-" + published.updatedAt(),
                "HOMEWORK_PUBLISHED",
                published.courseId(),
                studentIds,
                "作业已发布",
                "课程发布了新作业：" + published.title(),
                "HWK",
                published.id(),
                "/courses/" + published.courseId() + "/homeworks/" + published.id(),
                published.updatedAt()
        ));
        return published;
    }

    @Transactional
    public Homework close(long homeworkId, CurrentUser currentUser) {
        requireTeacher(currentUser);
        Homework existing = findExisting(homeworkId);
        requireManagePermission(existing.courseId(), currentUser.id());
        if (existing.status() != HomeworkStatus.PUBLISHED) {
            throw new ApiException("ERR-HWK-02", "当前作业状态不允许关闭", HttpStatus.BAD_REQUEST);
        }
        return homeworkRepository.update(existing.close(LocalDateTime.now()));
    }

    private Homework findExisting(long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(homework -> !homework.deleted())
                .orElseThrow(() -> new ApiException("ERR-HWK-04", "作业不存在", HttpStatus.NOT_FOUND));
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("教师无作业管理权限");
        }
    }

    private void requireManagePermission(long courseId, long userId) {
        if (!coursePermissionClient.canManageCourse(courseId, userId)) {
            throw new ApiException("ERR-HWK-01", "无课程作业管理权限", HttpStatus.FORBIDDEN);
        }
    }

    private void validate(CreateHomeworkCommand command) {
        List<String> errors = new ArrayList<>();
        if (command.courseId() <= 0) {
            errors.add("课程编号不能为空");
        }
        if (command.title() == null || command.title().trim().isEmpty() || command.title().trim().length() > 255) {
            errors.add("作业标题不能为空且长度不能超过 255");
        }
        if (command.description() == null || command.description().trim().isEmpty()) {
            errors.add("作业说明不能为空");
        }
        if (command.type() == null) {
            errors.add("作业类型不能为空");
        }
        if (command.totalScore() == null || command.totalScore().signum() <= 0) {
            errors.add("满分必须大于 0");
        }
        if (command.deadline() == null || !command.deadline().isAfter(LocalDateTime.now())) {
            errors.add("截止时间必须晚于当前时间");
        }
        if (!errors.isEmpty()) {
            throw new ApiException("ERR-HWK-03", String.join("；", errors), HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePublishReady(Homework homework) {
        if (homework.type() == HomeworkType.OBJECTIVE && homework.questions().isEmpty()) {
            throw new ApiException("ERR-HWK-03", "客观题作业至少配置一道题目", HttpStatus.BAD_REQUEST);
        }
        if (homework.type() == HomeworkType.CODE && homework.testCases().isEmpty()) {
            throw new ApiException("ERR-HWK-03", "代码作业至少配置一个测试用例", HttpStatus.BAD_REQUEST);
        }
    }
}
