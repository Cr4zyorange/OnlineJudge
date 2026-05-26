package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.CreateGradeItemCommand;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.GradeRuleValidationResult;
import com.onlinejudge.grd.domain.UpdateGradeItemCommand;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GradeItemService {
    private final GradeItemRepository gradeItemRepository;
    private final CoursePermissionClient coursePermissionClient;
    public GradeItemService(GradeItemRepository gradeItemRepository, CoursePermissionClient coursePermissionClient) {
        this.gradeItemRepository = gradeItemRepository;
        this.coursePermissionClient = coursePermissionClient;
    }

    public GradeItem createGradeItem(long courseId, long teacherId, CreateGradeItemCommand command) {
        requireCoursePermission(courseId, teacherId);
        validate(command);
        LocalDateTime now = LocalDateTime.now();
        GradeItem item = new GradeItem(
                0L,
                courseId,
                command.name().trim(),
                command.sourceType(),
                command.sourceId(),
                command.fullScore(),
                command.weight(),
                command.includedInFinal(),
                true,
                command.sortOrder(),
                teacherId,
                false,
                now,
                now
        );
        requireUniqueActiveName(courseId, item.name(), null);
        requireIncludedWeightWithinLimit(courseId, item, null);
        return gradeItemRepository.save(item);
    }

    public List<GradeItem> listGradeItems(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        return gradeItemRepository.findByCourseId(courseId);
    }

    public GradeItem updateGradeItem(long gradeItemId, long teacherId, UpdateGradeItemCommand command) {
        GradeItem existing = findExisting(gradeItemId);
        requireCoursePermission(existing.courseId(), teacherId);
        validate(command);
        GradeItem updated = existing.updateRule(
                command.name().trim(),
                command.sourceType(),
                command.sourceId(),
                command.fullScore(),
                command.weight(),
                command.includedInFinal(),
                command.sortOrder(),
                command.enabled() == null ? existing.enabled() : command.enabled(),
                LocalDateTime.now()
        );
        requireUniqueActiveName(existing.courseId(), updated.name(), existing.id());
        requireIncludedWeightWithinLimit(existing.courseId(), updated, existing.id());
        return gradeItemRepository.update(updated);
    }

    public GradeItem deleteGradeItem(long gradeItemId, long teacherId) {
        GradeItem existing = findExisting(gradeItemId);
        requireCoursePermission(existing.courseId(), teacherId);
        return gradeItemRepository.update(existing.disable(LocalDateTime.now()));
    }

    public GradeRuleValidationResult validateGradeRules(long courseId, long teacherId) {
        requireCoursePermission(courseId, teacherId);
        return validateGradeRules(gradeItemRepository.findByCourseId(courseId));
    }

    public GradeRuleValidationResult validateGradeRules(
            long courseId,
            long teacherId,
            List<CreateGradeItemCommand> candidateRules
    ) {
        requireCoursePermission(courseId, teacherId);
        if (candidateRules == null || candidateRules.isEmpty()) {
            return validateGradeRules(courseId, teacherId);
        }
        List<GradeItem> candidateItems = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (CreateGradeItemCommand candidateRule : candidateRules) {
            validate(candidateRule);
            candidateItems.add(new GradeItem(
                    0L,
                    courseId,
                    candidateRule.name().trim(),
                    candidateRule.sourceType(),
                    candidateRule.sourceId(),
                    candidateRule.fullScore(),
                    candidateRule.weight(),
                    candidateRule.includedInFinal(),
                    true,
                    candidateRule.sortOrder(),
                    teacherId,
                    false,
                    now,
                    now
            ));
        }
        return validateGradeRules(candidateItems);
    }

    private GradeRuleValidationResult validateGradeRules(List<GradeItem> items) {
        BigDecimal totalIncludedWeight = items.stream()
                .filter(GradeItem::enabled)
                .filter(GradeItem::includedInFinal)
                .map(GradeItem::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> errors = new ArrayList<>();
        if (totalIncludedWeight.compareTo(BigDecimal.ONE) > 0) {
            errors.add("计入总评的权重之和不能超过 1");
        }
        return new GradeRuleValidationResult(errors.isEmpty(), totalIncludedWeight, errors);
    }

    private void requireIncludedWeightWithinLimit(long courseId, GradeItem candidate, Long replacingGradeItemId) {
        List<GradeItem> candidateItems = new ArrayList<>();
        for (GradeItem item : gradeItemRepository.findByCourseId(courseId)) {
            if (replacingGradeItemId == null || item.id() != replacingGradeItemId) {
                candidateItems.add(item);
            }
        }
        candidateItems.add(candidate);
        GradeRuleValidationResult validationResult = validateGradeRules(candidateItems);
        if (!validationResult.valid()) {
            throw new InvalidGradeRuleException(String.join("；", validationResult.errors()));
        }
    }

    private void requireUniqueActiveName(long courseId, String candidateName, Long replacingGradeItemId) {
        boolean duplicateActiveName = gradeItemRepository.findByCourseId(courseId).stream()
                .filter(GradeItem::enabled)
                .filter(item -> replacingGradeItemId == null || item.id() != replacingGradeItemId)
                .anyMatch(item -> item.name().equals(candidateName));
        if (duplicateActiveName) {
            throw new InvalidGradeRuleException("同一课程下成绩项名称不能重复");
        }
    }

    public void validate(CreateGradeItemCommand command) {
        List<String> errors = new ArrayList<>();
        if (command.name() == null || command.name().trim().isEmpty()) {
            errors.add("成绩项名称不能为空");
        }
        if (command.sourceType() == null) {
            errors.add("来源类型不支持");
        }
        if (command.fullScore() == null || command.fullScore().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("满分值必须大于 0");
        }
        if (command.weight() == null
                || command.weight().compareTo(BigDecimal.ZERO) < 0
                || command.weight().compareTo(BigDecimal.ONE) > 0) {
            errors.add("权重必须在 0 到 1 之间");
        }
        if (command.sourceType() != null
                && command.sourceType().name().matches("LAB|HWK")
                && (command.sourceId() == null || command.sourceId() <= 0)) {
            errors.add("来源任务编号必须大于 0");
        }
        if (!errors.isEmpty()) {
            throw new InvalidGradeRuleException(String.join("；", errors));
        }
    }

    public void validate(UpdateGradeItemCommand command) {
        validate(new CreateGradeItemCommand(
                command.name(),
                command.sourceType(),
                command.sourceId(),
                command.fullScore(),
                command.weight(),
                command.includedInFinal(),
                command.sortOrder()
        ));
    }

    private GradeItem findExisting(long gradeItemId) {
        return gradeItemRepository.findById(gradeItemId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new GradeItemNotFoundException("成绩项不存在"));
    }

    private void requireCoursePermission(long courseId, long teacherId) {
        if (!coursePermissionClient.canManageCourseGrade(courseId, teacherId)) {
            throw new GradeItemPermissionException("教师无课程成绩管理权限");
        }
    }
}
