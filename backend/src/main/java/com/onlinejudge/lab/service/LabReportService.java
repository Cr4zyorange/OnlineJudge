package com.onlinejudge.lab.service;

import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabReport;
import com.onlinejudge.lab.domain.LabReportFileType;
import com.onlinejudge.lab.domain.LabReportRepository;
import com.onlinejudge.lab.domain.LabReportSubmitStatus;
import com.onlinejudge.lab.domain.LabReportSummaryView;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class LabReportService {
    private static final long MAX_REPORT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Map<String, LabReportFileType> SUPPORTED_REPORT_EXTENSIONS = Map.of(
            "pdf", LabReportFileType.PDF,
            "docx", LabReportFileType.DOCX,
            "zip", LabReportFileType.ZIP
    );

    private final LabExperimentRepository labExperimentRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabReportRepository labReportRepository;
    private final CoursePermissionClient coursePermissionClient;
    private final FileStorageService fileStorageService;

    public LabReportService(
            LabExperimentRepository labExperimentRepository,
            LabSubmissionRepository labSubmissionRepository,
            LabReportRepository labReportRepository,
            CoursePermissionClient coursePermissionClient,
            FileStorageService fileStorageService
    ) {
        this.labExperimentRepository = labExperimentRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labReportRepository = labReportRepository;
        this.coursePermissionClient = coursePermissionClient;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public LabReportSummaryView uploadReport(long labId, long studentId, Long submissionId, MultipartFile reportFile) throws IOException {
        LabExperiment experiment = findExistingExperiment(labId);
        requireStudentCanUpload(experiment, studentId);
        validateReportFile(reportFile);

        LabSubmission linkedSubmission = resolveSubmission(experiment, submissionId, studentId);
        LocalDateTime now = LocalDateTime.now();
        StoredFile storedFile = fileStorageService.store(
                reportFile.getOriginalFilename(),
                reportFile.getContentType(),
                reportFile.getInputStream()
        );
        int nextVersion = labReportRepository.findLatestByLabIdAndStudentId(labId, studentId)
                .map(existing -> existing.version() + 1)
                .orElse(1);
        LabReportFileType fileType = resolveFileType(reportFile.getOriginalFilename());
        LabReportSubmitStatus submitStatus = now.isAfter(experiment.deadline())
                ? LabReportSubmitStatus.LATE
                : LabReportSubmitStatus.SUBMITTED;

        LabReport saved = labReportRepository.save(new LabReport(
                0L,
                labId,
                studentId,
                linkedSubmission.id(),
                storedFile.storageKey(),
                sanitizeFilename(reportFile.getOriginalFilename()),
                fileType,
                storedFile.size(),
                nextVersion,
                submitStatus,
                null,
                null,
                now,
                null,
                null,
                now,
                now
        ));
        return LabReportSummaryView.from(saved, buildDownloadUrl(labId, saved.id()));
    }

    public LabReportSummaryView getReport(long labId, long reportId, long userId) {
        LabExperiment experiment = findExistingExperiment(labId);
        LabReport report = labReportRepository.findById(reportId)
                .orElseThrow(() -> new LabNotFoundException("实验报告不存在"));
        if (report.labId() != labId) {
            throw new LabNotFoundException("实验报告不存在");
        }

        boolean canManage = coursePermissionClient.canManageCourse(experiment.courseId(), userId);
        if (!canManage) {
            requireCourseViewPermission(experiment.courseId(), userId);
            if (report.studentId() != userId) {
                throw new LabPermissionException("无权限查看他人实验报告");
            }
        }
        return LabReportSummaryView.from(report, buildDownloadUrl(labId, report.id()));
    }

    public Optional<LabReportSummaryView> findLatestReport(long labId, long studentId) {
        return labReportRepository.findLatestByLabIdAndStudentId(labId, studentId)
                .map(report -> LabReportSummaryView.from(report, buildDownloadUrl(labId, report.id())));
    }

    public Optional<LabReportSummaryView> findLatestReportForSubmission(long submissionId) {
        return labReportRepository.findLatestBySubmissionId(submissionId)
                .map(report -> LabReportSummaryView.from(report, buildDownloadUrl(report.labId(), report.id())));
    }

    public StoredFile loadReportFile(long reportId) {
        LabReport report = labReportRepository.findById(reportId)
                .orElseThrow(() -> new LabNotFoundException("实验报告不存在"));
        return fileStorageService.load(report.fileId());
    }

    private LabExperiment findExistingExperiment(long labId) {
        return labExperimentRepository.findById(labId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new LabNotFoundException("实验不存在"));
    }

    private void requireStudentCanUpload(LabExperiment experiment, long studentId) {
        if (!coursePermissionClient.canViewCourse(experiment.courseId(), studentId)) {
            throw new LabPermissionException("无课程访问权限");
        }
        if (experiment.status() != LabExperimentStatus.PUBLISHED) {
            throw new LabStateException("当前实验状态不允许提交报告");
        }
    }

    private LabSubmission resolveSubmission(LabExperiment experiment, Long submissionId, long studentId) {
        if (submissionId == null) {
            return labSubmissionRepository.findLatestFinalByLabIdAndStudentId(experiment.id(), studentId)
                    .orElseThrow(() -> new LabNotFoundException("关联实验提交不存在"));
        }
        LabSubmission submission = labSubmissionRepository.findById(submissionId)
                .filter(item -> item.labId() == experiment.id())
                .orElseThrow(() -> new LabNotFoundException("关联实验提交不存在"));
        if (submission.studentId() != studentId) {
            throw new LabPermissionException("无权限关联他人提交");
        }
        return submission;
    }

    private void validateReportFile(MultipartFile reportFile) {
        if (reportFile == null || reportFile.isEmpty()) {
            throw new LabSubmissionValidationException("LAB-400-06", "报告文件不能为空");
        }
        if (reportFile.getSize() > MAX_REPORT_SIZE_BYTES) {
            throw new LabSubmissionValidationException("LAB-400-06", "报告文件大小超过系统限制");
        }
        resolveFileType(reportFile.getOriginalFilename());
    }

    private LabReportFileType resolveFileType(String originalFilename) {
        String extension = extractExtension(originalFilename);
        LabReportFileType fileType = SUPPORTED_REPORT_EXTENSIONS.get(extension);
        if (fileType == null) {
            throw new LabSubmissionValidationException("LAB-400-06", "报告文件格式不合法，仅支持 PDF、DOCX 或 ZIP");
        }
        return fileType;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int separatorIndex = fileName.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "report.bin";
        }
        return filename.replace("\\", "_").replace("/", "_").trim();
    }

    private String buildDownloadUrl(long labId, long reportId) {
        return "/api/v1/labs/" + labId + "/reports/" + reportId + "/download";
    }

    private void requireCourseViewPermission(long courseId, long userId) {
        if (!coursePermissionClient.canViewCourse(courseId, userId)) {
            throw new LabPermissionException("无课程访问权限");
        }
    }
}
