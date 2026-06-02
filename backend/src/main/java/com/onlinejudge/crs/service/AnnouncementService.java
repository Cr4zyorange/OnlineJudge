package com.onlinejudge.crs.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.common.exception.BusinessException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.crs.domain.Announcement;
import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.dto.AnnouncementRequest;
import com.onlinejudge.crs.domain.dto.AnnouncementResponse;
import com.onlinejudge.crs.domain.dto.CourseHomeSummaryResponse;
import com.onlinejudge.crs.mapper.AnnouncementRepository;
import com.onlinejudge.crs.mapper.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnnouncementService {
    private final AnnouncementRepository announcementRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final NotificationEventPublisher notificationEventPublisher;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               CourseRepository courseRepository,
                               CourseService courseService,
                               NotificationEventPublisher notificationEventPublisher) {
        this.announcementRepository = announcementRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional
    public AnnouncementResponse create(Long courseId, AnnouncementRequest request, CurrentUser user) {
        requireManagePermission(courseId, user);
        Announcement announcement = announcementRepository.insert(courseId, request, user.id(), sanitize(request.content()));
        notificationEventPublisher.publish(new NotificationEvent(
                "CRS_ANNOUNCEMENT_" + announcement.id(),
                "TEACHER_ANNOUNCEMENT",
                courseId,
                courseRepository.listActiveStudentIds(courseId),
                announcement.title(),
                announcement.content(),
                "CRS_ANNOUNCEMENT",
                announcement.id(),
                "/courses/" + courseId,
                LocalDateTime.now()
        ));
        return toResponse(announcement);
    }

    public List<AnnouncementResponse> list(Long courseId, CurrentUser user) {
        requireActiveMembership(courseId, user);
        return announcementRepository.listByCourse(courseId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CourseHomeSummaryResponse homeSummary(Long courseId, CurrentUser user) {
        requireActiveMembership(courseId, user);
        return new CourseHomeSummaryResponse(
                courseService.detail(courseId, user),
                list(courseId, user).stream().limit(5).toList(),
                List.of()
        );
    }

    @Transactional
    public AnnouncementResponse update(Long courseId, Long announcementId, AnnouncementRequest request, CurrentUser user) {
        requireManagePermission(courseId, user);
        requireAnnouncement(courseId, announcementId);
        return toResponse(announcementRepository.update(courseId, announcementId, request, sanitize(request.content())));
    }

    @Transactional
    public AnnouncementResponse updateTop(Long courseId, Long announcementId, boolean top, CurrentUser user) {
        requireManagePermission(courseId, user);
        requireAnnouncement(courseId, announcementId);
        return toResponse(announcementRepository.updateTop(courseId, announcementId, top));
    }

    @Transactional
    public void delete(Long courseId, Long announcementId, CurrentUser user) {
        requireManagePermission(courseId, user);
        requireAnnouncement(courseId, announcementId);
        announcementRepository.delete(courseId, announcementId);
    }

    private Announcement requireAnnouncement(Long courseId, Long announcementId) {
        return announcementRepository.findById(courseId, announcementId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND"));
    }

    private void requireManagePermission(Long courseId, CurrentUser user) {
        requireCourse(courseId);
        if (isAdmin(user)) {
            return;
        }
        CourseMember member = courseRepository.findMember(courseId, user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "NO_COURSE_MANAGE_PERMISSION"));
        if (member.status() != CourseMemberStatus.ACTIVE || member.role() != CourseMemberRole.TEACHER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "NO_COURSE_MANAGE_PERMISSION");
        }
    }

    private void requireActiveMembership(Long courseId, CurrentUser user) {
        requireCourse(courseId);
        if (isAdmin(user)) {
            return;
        }
        if (courseRepository.findMember(courseId, user.id())
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                .isEmpty()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "NO_COURSE_MEMBERSHIP");
        }
    }

    private void requireCourse(Long courseId) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));
    }

    private String sanitize(String content) {
        String sanitized = content == null ? "" : content.trim();
        sanitized = sanitized.replaceAll("(?is)<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>", "");
        sanitized = sanitized.replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*\"[^\"]*\"", "");
        sanitized = sanitized.replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*'[^']*'", "");
        if (sanitized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ANNOUNCEMENT_CONTENT_REQUIRED");
        }
        if (sanitized.length() > 5000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ANNOUNCEMENT_CONTENT_TOO_LONG");
        }
        return sanitized;
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.id(),
                announcement.courseId(),
                announcement.title(),
                announcement.content(),
                announcement.top(),
                announcement.publisherId(),
                "教师" + announcement.publisherId(),
                announcement.createdAt(),
                announcement.updatedAt()
        );
    }

    private boolean isAdmin(CurrentUser user) {
        return user.hasRole("ADMIN");
    }
}
