package com.onlinejudge.lab.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.CreateLabExperimentCommand;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabTestcase;
import com.onlinejudge.lab.domain.LabTestcaseDraft;
import com.onlinejudge.lab.domain.UpdateLabExperimentCommand;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class LabExperimentService {
    private final LabExperimentRepository repository;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public LabExperimentService(
            LabExperimentRepository repository,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.repository = repository;
        this.coursePermissionClient = coursePermissionClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    public LabExperiment createLab(long courseId, long teacherId, CreateLabExperimentCommand command) {
        requireManagePermission(courseId, teacherId);
        validate(command);
        LocalDateTime now = LocalDateTime.now();
        return repository.save(new LabExperiment(
                0L,
                courseId,
                command.chapterId(),
                command.title().trim(),
                command.description().trim(),
                LabExperimentStatus.DRAFT,
                command.deadline(),
                command.maxScore(),
                normalizeAttachmentIds(command.attachmentIds()),
                normalizeAllowedLanguages(command.allowedLanguages()),
                command.evaluationMode(),
                command.autoEvaluate(),
                command.reportRequired(),
                command.timeLimitMs(),
                command.memoryLimitKb(),
                teacherId,
                false,
                now,
                now,
                toTestcases(command.testcases(), now)
        ));
    }

    public List<LabExperiment> listLabs(long courseId, long userId, LabExperimentStatus status) {
        requireViewPermission(courseId, userId);
        return repository.findByCourseId(courseId, status);
    }

    public LabExperiment getLab(long labId, long userId) {
        LabExperiment experiment = findExisting(labId);
        requireViewPermission(experiment.courseId(), userId);
        return experiment;
    }

    public LabExperiment updateLab(long labId, long teacherId, UpdateLabExperimentCommand command) {
        LabExperiment existing = findExisting(labId);
        requireManagePermission(existing.courseId(), teacherId);
        requireEditable(existing);
        validate(command);
        return repository.update(existing.update(
                command.chapterId(),
                command.title().trim(),
                command.description().trim(),
                command.deadline(),
                command.maxScore(),
                normalizeAttachmentIds(command.attachmentIds()),
                normalizeAllowedLanguages(command.allowedLanguages()),
                command.evaluationMode(),
                command.autoEvaluate(),
                command.reportRequired(),
                command.timeLimitMs(),
                command.memoryLimitKb(),
                LocalDateTime.now(),
                toTestcases(command.testcases(), LocalDateTime.now())
        ));
    }

    public LabExperiment deleteLab(long labId, long teacherId) {
        LabExperiment existing = findExisting(labId);
        requireManagePermission(existing.courseId(), teacherId);
        if (existing.status() != LabExperimentStatus.DRAFT) {
            throw new LabStateException("仅草稿实验允许删除");
        }
        return repository.update(existing.delete(LocalDateTime.now()));
    }

    public LabExperiment publishLab(long labId, long teacherId) {
        LabExperiment existing = findExisting(labId);
        requireManagePermission(existing.courseId(), teacherId);
        if (existing.status() != LabExperimentStatus.DRAFT) {
            throw new LabStateException("当前实验状态不允许发布");
        }
        LabExperiment published = repository.update(existing.publish(LocalDateTime.now()));
        notificationEventPublisher.publish(new NotificationEvent(
                "lab-published-" + published.id() + "-" + published.updatedAt(),
                "LAB_EXPERIMENT_PUBLISHED",
                published.courseId(),
                List.of(),
                "实验已发布",
                "课程发布了新实验：" + published.title(),
                "LAB",
                published.id(),
                "/courses/" + published.courseId() + "/labs/" + published.id(),
                published.updatedAt()
        ));
        return published;
    }

    public LabExperiment closeLab(long labId, long teacherId) {
        LabExperiment existing = findExisting(labId);
        requireManagePermission(existing.courseId(), teacherId);
        if (existing.status() != LabExperimentStatus.PUBLISHED) {
            throw new LabStateException("当前实验状态不允许截止");
        }
        return repository.update(existing.close(LocalDateTime.now()));
    }

    private LabExperiment findExisting(long labId) {
        return repository.findById(labId)
                .filter(experiment -> !experiment.deleted())
                .orElseThrow(() -> new LabNotFoundException("实验不存在"));
    }

    private void requireManagePermission(long courseId, long userId) {
        if (!coursePermissionClient.canManageCourse(courseId, userId)) {
            throw new LabPermissionException("无课程管理权限");
        }
    }

    private void requireViewPermission(long courseId, long userId) {
        if (!coursePermissionClient.canViewCourse(courseId, userId)) {
            throw new LabPermissionException("无课程访问权限");
        }
    }

    private void requireEditable(LabExperiment experiment) {
        if (experiment.status() != LabExperimentStatus.DRAFT) {
            throw new LabStateException("仅草稿实验允许修改");
        }
    }

    private void validate(CreateLabExperimentCommand command) {
        List<String> errors = new ArrayList<>();
        if (command.title() == null || command.title().trim().isEmpty() || command.title().trim().length() > 100) {
            errors.add("实验名称不能为空且长度不能超过 100");
        }
        if (command.description() == null || command.description().trim().isEmpty()) {
            errors.add("实验说明不能为空");
        }
        if (command.deadline() == null || !command.deadline().isAfter(LocalDateTime.now())) {
            errors.add("截止时间必须晚于当前时间");
        }
        if (command.maxScore() <= 0) {
            errors.add("满分必须大于 0");
        }
        if (command.evaluationMode() == null) {
            errors.add("评测方式不能为空");
        }
        if (command.timeLimitMs() <= 0) {
            errors.add("时间限制必须大于 0");
        }
        if (command.memoryLimitKb() <= 0) {
            errors.add("内存限制必须大于 0");
        }
        validateTestcases(command.testcases(), errors);
        if (!errors.isEmpty()) {
            throw new LabValidationException(String.join("；", errors));
        }
    }

    private void validate(UpdateLabExperimentCommand command) {
        validate(new CreateLabExperimentCommand(
                command.chapterId(),
                command.title(),
                command.description(),
                command.deadline(),
                command.maxScore(),
                command.attachmentIds(),
                command.allowedLanguages(),
                command.evaluationMode(),
                command.autoEvaluate(),
                command.reportRequired(),
                command.timeLimitMs(),
                command.memoryLimitKb(),
                command.testcases()
        ));
    }

    private void validateTestcases(List<LabTestcaseDraft> testcases, List<String> errors) {
        if (testcases == null) {
            return;
        }
        for (int index = 0; index < testcases.size(); index++) {
            LabTestcaseDraft testcase = testcases.get(index);
            if (testcase.input() == null || testcase.expectedOutput() == null) {
                errors.add("测试用例输入和输出不能为空");
            }
            if (testcase.scoreWeight() < 0) {
                errors.add("测试用例分值不能为负数");
            }
            if (testcase.timeLimitMs() <= 0) {
                errors.add("测试用例时间限制必须大于 0");
            }
            if (testcase.memoryLimitKb() <= 0) {
                errors.add("测试用例内存限制必须大于 0");
            }
            if (testcase.orderNum() < 0) {
                errors.add("测试用例排序必须为非负整数");
            }
        }
    }

    private List<Long> normalizeAttachmentIds(List<Long> attachmentIds) {
        if (attachmentIds == null) {
            return List.of();
        }
        return attachmentIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String normalizeAllowedLanguages(String allowedLanguages) {
        if (allowedLanguages == null) {
            return null;
        }
        String value = allowedLanguages.trim();
        return value.isEmpty() ? null : value;
    }

    private List<LabTestcase> toTestcases(List<LabTestcaseDraft> drafts, LocalDateTime now) {
        if (drafts == null) {
            return List.of();
        }
        return drafts.stream()
                .map(draft -> new LabTestcase(
                        0L,
                        0L,
                        draft.input(),
                        draft.expectedOutput(),
                        draft.scoreWeight(),
                        draft.isPublic(),
                        draft.timeLimitMs(),
                        draft.memoryLimitKb(),
                        draft.orderNum(),
                        false,
                        now,
                        now
                ))
                .toList();
    }
}
