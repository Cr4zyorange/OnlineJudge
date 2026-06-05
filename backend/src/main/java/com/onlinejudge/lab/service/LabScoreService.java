package com.onlinejudge.lab.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabReport;
import com.onlinejudge.lab.domain.LabReportRepository;
import com.onlinejudge.lab.domain.LabScore;
import com.onlinejudge.lab.domain.LabScoreChangeLog;
import com.onlinejudge.lab.domain.LabScoreChangeLogRepository;
import com.onlinejudge.lab.domain.LabScoreRepository;
import com.onlinejudge.lab.domain.LabScoreSummaryView;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.ScoreLabSubmissionCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LabScoreService {
    private static final int MAX_COMMENT_LENGTH = 500;
    private static final int MAX_REASON_LENGTH = 500;

    private final LabExperimentRepository labExperimentRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabReportRepository labReportRepository;
    private final LabScoreRepository labScoreRepository;
    private final LabScoreChangeLogRepository labScoreChangeLogRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public LabScoreService(
            LabExperimentRepository labExperimentRepository,
            LabSubmissionRepository labSubmissionRepository,
            LabReportRepository labReportRepository,
            LabScoreRepository labScoreRepository,
            LabScoreChangeLogRepository labScoreChangeLogRepository,
            CoursePermissionClient coursePermissionClient,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.labExperimentRepository = labExperimentRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labReportRepository = labReportRepository;
        this.labScoreRepository = labScoreRepository;
        this.labScoreChangeLogRepository = labScoreChangeLogRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional
    public LabScoreSummaryView scoreSubmission(long labId, long submissionId, long teacherId, ScoreLabSubmissionCommand command) {
        LabExperiment experiment = labExperimentRepository.findById(labId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("实验不存在"));
        if (!coursePermissionClient.canManageCourse(experiment.courseId(), teacherId)) {
            throw new LabPermissionException("无课程管理权限");
        }

        LabSubmission submission = labSubmissionRepository.findById(submissionId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("提交不存在"));
        if (submission.labId() != labId) {
            throw new LabNotFoundException("提交不存在");
        }

        ValidatedSubmissionScore validated = validateCommand(command, experiment.maxScore());
        Optional<LabReport> latestReport = labReportRepository.findLatestBySubmissionId(submissionId);
        Integer reportScore = resolveReportScore(latestReport, validated.reportScore(), experiment.maxScore());
        LocalDateTime now = LocalDateTime.now();

        latestReport.ifPresent(report -> updateReportScoreIfNeeded(report, validated.reportScore(), teacherId, now));

        LabScore savedScore;
        Optional<LabScore> existingScore = labScoreRepository.findBySubmissionId(submissionId);
        if (existingScore.isPresent()) {
            LabScore current = existingScore.get();
            boolean changed = isChanged(current, validated, reportScore);
            String changeReason = normalizeChangeReason(validated.changeReason(), changed);
            savedScore = labScoreRepository.update(new LabScore(
                    current.id(),
                    current.submissionId(),
                    latestReport.map(LabReport::id).orElse(null),
                    teacherId,
                    submission.autoScore(),
                    reportScore,
                    validated.manualScore(),
                    validated.finalScore(),
                    validated.comment(),
                    now,
                    now
            ));
            if (changed) {
                labScoreChangeLogRepository.save(new LabScoreChangeLog(
                        0L,
                        current.id(),
                        current.finalScore(),
                        validated.finalScore(),
                        changeReason,
                        teacherId,
                        now
                ));
            }
        } else {
            savedScore = labScoreRepository.save(new LabScore(
                    0L,
                    submissionId,
                    latestReport.map(LabReport::id).orElse(null),
                    teacherId,
                    submission.autoScore(),
                    reportScore,
                    validated.manualScore(),
                    validated.finalScore(),
                    validated.comment(),
                    now,
                    now
            ));
        }

        labSubmissionRepository.update(submission.withEvaluationResult(
                submission.evaluationStatus(),
                submission.autoScore(),
                validated.finalScore(),
                now
        ));
        publishScoreNotification(experiment, submission, validated.finalScore(), now);
        return toSummaryView(savedScore);
    }

    public Optional<LabScoreSummaryView> findLatestScore(long submissionId) {
        return labScoreRepository.findBySubmissionId(submissionId)
                .map(this::toSummaryView);
    }

    private LabScoreSummaryView toSummaryView(LabScore score) {
        return LabScoreSummaryView.from(score, labScoreChangeLogRepository.countByScoreId(score.id()) > 0);
    }

    private ValidatedSubmissionScore validateCommand(ScoreLabSubmissionCommand command, int maxScore) {
        if (command == null) {
            throw new LabSubmissionValidationException("LAB-400-05", "教师评分不能为空");
        }
        Integer manualScore = requireScore(command.manualScore(), maxScore, "人工评分");
        Integer reportScore = optionalScore(command.reportScore(), maxScore, "报告评分");
        Integer finalScore = requireScore(command.finalScore(), maxScore, "最终得分");
        String comment = normalizeComment(command.comment());
        return new ValidatedSubmissionScore(manualScore, reportScore, finalScore, comment, command.changeReason());
    }

    private Integer requireScore(Integer value, int maxScore, String label) {
        if (value == null) {
            throw new LabSubmissionValidationException("LAB-400-05", label + "不能为空");
        }
        return validateScoreRange(value, maxScore, label);
    }

    private Integer optionalScore(Integer value, int maxScore, String label) {
        if (value == null) {
            return null;
        }
        return validateScoreRange(value, maxScore, label);
    }

    private Integer validateScoreRange(Integer value, int maxScore, String label) {
        if (value < 0 || value > maxScore) {
            throw new LabSubmissionValidationException("LAB-400-05", label + "必须在 0 到 " + maxScore + " 之间");
        }
        return value;
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            throw new LabSubmissionValidationException("LAB-400-05", "教师评语不能超过 500 字");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeChangeReason(String changeReason, boolean changed) {
        if (!changed) {
            return null;
        }
        if (changeReason == null || changeReason.trim().isEmpty()) {
            throw new LabSubmissionValidationException("LAB-400-05", "修改已评分记录时必须填写修改原因");
        }
        String normalized = changeReason.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new LabSubmissionValidationException("LAB-400-05", "修改原因不能超过 500 字");
        }
        return normalized;
    }

    private Integer resolveReportScore(Optional<LabReport> latestReport, Integer requestedScore, int maxScore) {
        if (requestedScore == null) {
            return latestReport.map(LabReport::score).orElse(null);
        }
        if (latestReport.isEmpty()) {
            throw new LabSubmissionValidationException("LAB-400-05", "当前提交不存在实验报告，不能填写报告评分");
        }
        return validateScoreRange(requestedScore, maxScore, "报告评分");
    }

    private void updateReportScoreIfNeeded(LabReport report, Integer requestedScore, long teacherId, LocalDateTime now) {
        if (requestedScore == null || Objects.equals(report.score(), requestedScore)) {
            return;
        }
        labReportRepository.updateScore(new LabReport(
                report.id(),
                report.labId(),
                report.studentId(),
                report.submissionId(),
                report.fileId(),
                report.fileName(),
                report.fileType(),
                report.fileSize(),
                report.version(),
                report.submitStatus(),
                requestedScore,
                report.comment(),
                report.submittedAt(),
                teacherId,
                now,
                report.createdAt(),
                now
        ));
    }

    private boolean isChanged(LabScore current, ValidatedSubmissionScore validated, Integer reportScore) {
        return !Objects.equals(current.manualScore(), validated.manualScore())
                || !Objects.equals(current.reportScore(), reportScore)
                || current.finalScore() != validated.finalScore()
                || !Objects.equals(current.comment(), validated.comment());
    }

    private void publishScoreNotification(LabExperiment experiment, LabSubmission submission, int finalScore, LocalDateTime occurredAt) {
        notificationEventPublisher.publish(new NotificationEvent(
                "lab-score-" + submission.id() + "-" + occurredAt,
                "LAB_SUBMISSION_SCORED",
                experiment.courseId(),
                List.of(submission.studentId()),
                "实验评分已更新",
                "实验《" + experiment.title() + "》已完成评分，当前最终分为 " + finalScore,
                "LAB",
                experiment.id(),
                "/courses/" + experiment.courseId() + "/labs/" + experiment.id(),
                occurredAt
        ));
    }

    private record ValidatedSubmissionScore(
            Integer manualScore,
            Integer reportScore,
            int finalScore,
            String comment,
            String changeReason
    ) {
    }
}
