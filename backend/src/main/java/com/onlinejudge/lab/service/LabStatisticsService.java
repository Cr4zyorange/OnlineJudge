package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabStatisticsView;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LabStatisticsService {
    private static final Comparator<LabSubmission> SUBMISSION_ORDER = Comparator
            .comparing(LabSubmission::submittedAt)
            .thenComparingInt(LabSubmission::version)
            .thenComparingLong(LabSubmission::id);
    private static final List<String> SCORE_BUCKETS = List.of("0-59", "60-69", "70-79", "80-89", "90-100");

    private final LabExperimentRepository labExperimentRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final CoursePermissionClient coursePermissionClient;

    public LabStatisticsService(
            LabExperimentRepository labExperimentRepository,
            LabSubmissionRepository labSubmissionRepository,
            CoursePermissionClient coursePermissionClient
    ) {
        this.labExperimentRepository = labExperimentRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.coursePermissionClient = coursePermissionClient;
    }

    public LabStatisticsView getStatistics(long labId, long teacherId) {
        LabExperiment experiment = labExperimentRepository.findById(labId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("实验不存在"));
        if (!coursePermissionClient.canManageCourse(experiment.courseId(), teacherId)) {
            throw new LabPermissionException("无课程管理权限");
        }

        List<Long> courseStudentIds = coursePermissionClient.listCourseStudentIds(experiment.courseId()).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<LabSubmission> finalSubmissions = finalSubmissions(labId);
        Set<Long> submittedStudentIds = finalSubmissions.stream()
                .map(LabSubmission::studentId)
                .collect(Collectors.toSet());
        List<Long> unsubmittedStudentIds = courseStudentIds.stream()
                .filter(studentId -> !submittedStudentIds.contains(studentId))
                .toList();

        int totalStudentCount = courseStudentIds.isEmpty() ? submittedStudentIds.size() : courseStudentIds.size();
        int submittedCount = submittedStudentIds.size();
        int unsubmittedCount = Math.max(totalStudentCount - submittedCount, unsubmittedStudentIds.size());
        int evaluatedCount = (int) finalSubmissions.stream()
                .filter(this::evaluationCompleted)
                .count();
        int lateSubmissionCount = (int) finalSubmissions.stream()
                .filter(submission -> isLateSubmission(submission, experiment.deadline()))
                .count();

        List<Integer> effectiveScores = finalSubmissions.stream()
                .map(this::effectiveScore)
                .flatMap(Optional::stream)
                .toList();

        return new LabStatisticsView(
                labId,
                experiment.courseId(),
                totalStudentCount,
                submittedCount,
                unsubmittedCount,
                evaluatedCount,
                rate(submittedCount, totalStudentCount),
                rate(evaluatedCount, totalStudentCount),
                average(effectiveScores),
                lateSubmissionCount,
                unsubmittedStudentIds,
                scoreDistribution(effectiveScores),
                LocalDateTime.now()
        );
    }

    private List<LabSubmission> finalSubmissions(long labId) {
        return labSubmissionRepository.findByLabId(labId).stream()
                .filter(submission -> !submission.deleted())
                .collect(Collectors.toMap(
                        LabSubmission::studentId,
                        Function.identity(),
                        (left, right) -> {
                            if (left.isFinal() && !right.isFinal()) {
                                return left;
                            }
                            if (!left.isFinal() && right.isFinal()) {
                                return right;
                            }
                            return SUBMISSION_ORDER.compare(left, right) >= 0 ? left : right;
                        },
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingLong(LabSubmission::studentId))
                .toList();
    }

    private boolean evaluationCompleted(LabSubmission submission) {
        return submission.evaluationStatus() != EvaluationStatus.NONE
                && submission.evaluationStatus() != EvaluationStatus.PENDING
                && submission.evaluationStatus() != EvaluationStatus.RUNNING;
    }

    private boolean isLateSubmission(LabSubmission submission, LocalDateTime deadline) {
        return submission.submitStatus().name().equals("LATE") || submission.submittedAt().isAfter(deadline);
    }

    private Optional<Integer> effectiveScore(LabSubmission submission) {
        if (submission.finalScore() != null) {
            return Optional.of(submission.finalScore());
        }
        return Optional.ofNullable(submission.autoScore());
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<Integer> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        return scores.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Integer> scoreDistribution(List<Integer> scores) {
        Map<String, Integer> buckets = new LinkedHashMap<>();
        SCORE_BUCKETS.forEach(bucket -> buckets.put(bucket, 0));
        for (Integer score : scores) {
            buckets.compute(scoreBucket(score), (key, value) -> value == null ? 1 : value + 1);
        }
        return buckets;
    }

    private String scoreBucket(int score) {
        if (score >= 90) {
            return "90-100";
        }
        if (score >= 80) {
            return "80-89";
        }
        if (score >= 70) {
            return "70-79";
        }
        if (score >= 60) {
            return "60-69";
        }
        return "0-59";
    }
}
