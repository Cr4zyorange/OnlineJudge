package com.onlinejudge.courseservice.service;

import com.onlinejudge.courseservice.persistence.CourseOutboxRepository;
import com.onlinejudge.courseservice.persistence.CourseRosterReconciliationRepository;
import com.onlinejudge.courseservice.persistence.CourseRepository;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Course owns DB-CRS-01..05 and the canonical member/roster producer facts. */
@Service
public class CourseService {
    private static final Duration RECONCILIATION_INTERVAL = Duration.ofMinutes(5);
    private final CourseRepository courses;
    private final CourseOutboxRepository outbox;
    private final CourseRosterReconciliationRepository reconciliation;
    private final CourseFileStorage fileStorage;

    public CourseService(CourseRepository courses, CourseOutboxRepository outbox, CourseRosterReconciliationRepository reconciliation,
                         CourseFileStorage fileStorage) {
        this.courses = courses;
        this.outbox = outbox;
        this.reconciliation = reconciliation;
        this.fileStorage = fileStorage;
    }

    @Transactional
    public CourseView create(String name, String description, String enrollmentMode, String inviteCode, Integer maxStudents,
                             CurrentUser actor, String correlationId) {
        requireTeacher(actor);
        String normalizedName = courseName(name);
        String mode = enrollmentMode(enrollmentMode, "PUBLIC");
        Integer capacity = capacity(maxStudents);
        String code = inviteCode(mode, inviteCode, null);
        long courseId = courses.createCourse(normalizedName, description(description), actor.id(), mode, code, capacity);
        CourseRepository.Member teacher = courses.insertMember(courseId, actor.id(), "TEACHER", "ACTIVE", "CREATED", actor.id());
        writeMemberFacts(teacher, correlationId);
        writeRosterSnapshot(courseId, correlationId);
        return view(course(courseId), actor);
    }

    @Transactional
    public CourseView update(long courseId, String name, String description, String enrollmentMode, String inviteCode,
                             Integer maxStudents, String status, CurrentUser actor) {
        requireOwner(courseId, actor);
        CourseRepository.Course current = course(courseId);
        String nextName = name == null ? current.name() : courseName(name);
        String nextDescription = description == null ? current.description() : description(description);
        String nextMode = enrollmentMode == null ? current.enrollmentMode() : enrollmentMode(enrollmentMode, current.enrollmentMode());
        String nextStatus = status == null || status.isBlank() ? current.status() : courseStatus(status);
        Integer nextCapacity = maxStudents == null ? current.maxStudents() : capacity(maxStudents);
        String nextInvite = inviteCode(nextMode, inviteCode, current.inviteCode());
        return view(courses.updateCourse(courseId, nextName, nextDescription, nextMode, nextInvite, nextCapacity, nextStatus), actor);
    }

    @Transactional
    public CourseView archive(long courseId, CurrentUser actor) {
        requireOwner(courseId, actor);
        return view(courses.archiveCourse(courseId), actor);
    }

    @Transactional
    public MemberView join(long courseId, String inviteCode, CurrentUser actor, String correlationId) {
        CourseRepository.Course course = course(courseId);
        if (!"ACTIVE".equals(course.status())) {
            throw new CourseException(HttpStatus.CONFLICT, "COURSE_CLOSED", "course is not open for enrollment", false);
        }
        if ("INVITE".equals(course.enrollmentMode()) && !constantTimeEquals(course.inviteCode(), inviteCode)) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "INVALID_INVITE_CODE", "invite code is invalid", false);
        }
        CourseRepository.Member existing = courses.member(courseId, actor.id()).orElse(null);
        if (existing != null && ("ACTIVE".equals(existing.status()) || "PENDING".equals(existing.status()))) {
            throw new CourseException(HttpStatus.CONFLICT, "ALREADY_JOINED", "course membership already exists", false);
        }
        if (course.maxStudents() != null && courses.activeMemberCount(courseId, null) >= course.maxStudents()) {
            throw new CourseException(HttpStatus.CONFLICT, "COURSE_CAPACITY_REACHED", "course has reached its member capacity", false);
        }
        String nextStatus = "REVIEW".equals(course.enrollmentMode()) ? "PENDING" : "ACTIVE";
        CourseRepository.Member member = existing == null
                ? courses.insertMember(courseId, actor.id(), "STUDENT", nextStatus, joinMethod(course.enrollmentMode()), null)
                : courses.updateMember(courseId, actor.id(), existing.role(), nextStatus, null);
        writeMemberFacts(member, correlationId);
        writeRosterSnapshot(courseId, correlationId);
        return memberView(member);
    }

    @Transactional
    public MemberView leave(long courseId, CurrentUser actor, String correlationId) {
        CourseRepository.Member current = courses.member(courseId, actor.id()).orElseThrow(this::forbidden);
        if (!"ACTIVE".equals(current.status()) || "TEACHER".equals(current.role())) throw forbidden();
        CourseRepository.Member changed = courses.updateMember(courseId, actor.id(), current.role(), "REMOVED", null);
        writeMemberFacts(changed, correlationId);
        writeRosterSnapshot(courseId, correlationId);
        return memberView(changed);
    }

    @Transactional
    public MemberView changeMember(long courseId, long userId, String role, String status, CurrentUser actor, String correlationId) {
        requireOwner(courseId, actor);
        CourseRepository.Member current = courses.member(courseId, userId)
                .orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "COURSE_MEMBER_NOT_FOUND", "course member does not exist", false));
        String nextRole = role == null || role.isBlank() ? current.role() : memberRole(role);
        String nextStatus = status == null || status.isBlank() ? current.status() : memberStatus(status);
        if (current.role().equals("TEACHER") && "ACTIVE".equals(current.status())
                && (!"TEACHER".equals(nextRole) || !"ACTIVE".equals(nextStatus))
                && courses.activeMemberCount(courseId, "TEACHER") <= 1) {
            throw new CourseException(HttpStatus.CONFLICT, "LAST_TEACHER_REQUIRED", "a course requires one active teacher", false);
        }
        CourseRepository.Member changed = courses.updateMember(courseId, userId, nextRole, nextStatus,
                "ACTIVE".equals(nextStatus) ? actor.id() : null);
        writeMemberFacts(changed, correlationId);
        writeRosterSnapshot(courseId, correlationId);
        return memberView(changed);
    }

    public CourseView detail(long courseId, CurrentUser actor) {
        CourseRepository.Course course = course(courseId);
        if (!canViewCourse(course, actor)) throw forbidden();
        return view(course, actor);
    }

    public CoursePage list(String keyword, int page, int size, CurrentUser actor) {
        if (page < 0 || size < 1 || size > 100) throw new CourseException(HttpStatus.BAD_REQUEST, "PAGE_INVALID", "page and size are invalid", false);
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<CourseView> all = courses.allCourses().stream()
                .filter(course -> canListCourse(course, actor))
                .filter(course -> needle.isEmpty() || course.name().toLowerCase(Locale.ROOT).contains(needle))
                .map(course -> view(course, actor)).toList();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new CoursePage(all.subList(from, to), page, size, all.size());
    }

    @Transactional
    public ChapterView createChapter(long courseId, String title, String parentId, Integer sortOrder, String objective,
                                     Boolean visible, Integer chapterType, CurrentUser actor) {
        requireContentManager(courseId, actor);
        Long parent = validateParent(courseId, null, parentId);
        int order = order(sortOrder);
        int type = chapterType == null ? 1 : chapterType;
        if (type < 1 || type > 9) throw new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_TYPE_INVALID", "chapter type is invalid", false);
        return chapterView(courses.createChapter(courseId, chapterTitle(title), parent, order, objective(objective), visible == null || visible, type));
    }

    @Transactional
    public ChapterView updateChapter(long courseId, long chapterId, String title, String parentId, Integer sortOrder,
                                     String objective, Boolean visible, Integer chapterType, CurrentUser actor) {
        requireContentManager(courseId, actor);
        CourseRepository.Chapter current = courses.chapter(courseId, chapterId)
                .orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "chapter does not exist", false));
        Long parent = parentId == null ? current.parentId() : validateParent(courseId, chapterId, parentId);
        int type = chapterType == null ? current.chapterType() : chapterType;
        if (type < 1 || type > 9) throw new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_TYPE_INVALID", "chapter type is invalid", false);
        courses.updateChapter(courseId, chapterId, title == null ? current.title() : chapterTitle(title), parent,
                sortOrder == null ? current.sortOrder() : order(sortOrder), objective == null ? current.objective() : objective(objective),
                visible == null ? current.visible() : visible, type);
        return chapterView(courses.chapter(courseId, chapterId).orElseThrow());
    }

    @Transactional
    public void deleteChapter(long courseId, long chapterId, CurrentUser actor) {
        requireContentManager(courseId, actor);
        courses.chapter(courseId, chapterId).orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "chapter does not exist", false));
        if (courses.chapterHasChildren(courseId, chapterId)) {
            throw new CourseException(HttpStatus.CONFLICT, "CHAPTER_HAS_CHILDREN", "remove child chapters before deleting this chapter", false);
        }
        courses.deleteChapter(courseId, chapterId);
    }

    public List<ChapterView> chapters(long courseId, CurrentUser actor) {
        requireMember(courseId, actor);
        return courses.chapters(courseId, canManageContent(courseId, actor)).stream().map(this::chapterView).toList();
    }

    @Transactional
    public ResourceView createResource(long courseId, String title, String url, String chapterId, String resourceType,
                                       String visibility, LocalDateTime publishAt, CurrentUser actor) {
        requireContentManager(courseId, actor);
        Long chapter = validateResourceChapter(courseId, chapterId);
        String normalizedUrl = resourceUrl(url);
        String normalizedTitle = resourceTitle(title);
        String type = resourceType == null || resourceType.isBlank() ? "LINK" : resourceType(resourceType);
        String visible = resourceVisibility(visibility, "STUDENT");
        return resourceView(courses.createResource(courseId, chapter, normalizedTitle, type, visible, publishAt, normalizedUrl, normalizedUrl,
                normalizedTitle, "text/uri-list", 0, actor.id()));
    }

    @Transactional
    public ResourceView uploadResource(long courseId, MultipartFile file, String title, String chapterId, String resourceType,
                                       String visibility, LocalDateTime publishAt, CurrentUser actor) {
        requireContentManager(courseId, actor);
        Long chapter = validateResourceChapter(courseId, chapterId);
        CourseFileStorage.StoredFile stored = fileStorage.store(file);
        try {
            String name = title == null || title.isBlank() ? stored.originalFilename() : resourceTitle(title);
            String type = resourceType == null || resourceType.isBlank() ? resourceTypeFromFilename(stored.originalFilename()) : resourceType(resourceType);
            return resourceView(courses.createResource(courseId, chapter, name, type, resourceVisibility(visibility, "STUDENT"), publishAt,
                    stored.storageKey(), null, stored.originalFilename(), stored.contentType(), stored.size(), actor.id()));
        } catch (RuntimeException failure) {
            fileStorage.deleteQuietly(stored.storageKey());
            throw failure;
        }
    }

    @Transactional
    public ResourceView updateResource(long courseId, long resourceId, String title, String url, String chapterId,
                                       String resourceType, String visibility, LocalDateTime publishAt, CurrentUser actor) {
        requireContentManager(courseId, actor);
        CourseRepository.Resource current = resource(courseId, resourceId);
        Long chapter = chapterId == null ? current.chapterId() : validateResourceChapter(courseId, chapterId);
        String nextUrl = url == null ? current.externalUrl() : resourceUrl(url);
        courses.updateResource(courseId, resourceId, chapter, title == null ? current.name() : resourceTitle(title),
                resourceType == null ? current.type() : resourceType(resourceType),
                resourceVisibility(visibility, current.visibility()), publishAt == null ? current.publishAt() : publishAt, nextUrl);
        return resourceView(resource(courseId, resourceId));
    }

    @Transactional
    public void deleteResource(long courseId, long resourceId, CurrentUser actor) {
        requireContentManager(courseId, actor);
        resource(courseId, resourceId);
        courses.deleteResource(courseId, resourceId);
    }

    public List<ResourceView> resources(long courseId, CurrentUser actor) {
        requireMember(courseId, actor);
        return courses.resources(courseId, canManageContent(courseId, actor)).stream().map(this::resourceView).toList();
    }

    /** HTTP GET must remain safe for browser prefetches and retries. */
    public ResourceDownload downloadResource(long courseId, long resourceId, CurrentUser actor) {
        requireMember(courseId, actor);
        CourseRepository.Resource resource = resource(courseId, resourceId);
        if (!canManageContent(courseId, actor) && (!"STUDENT".equals(resource.visibility())
                || (resource.publishAt() != null && resource.publishAt().isAfter(LocalDateTime.now())))) throw forbidden();
        if (resource.externalUrl() != null && !resource.externalUrl().isBlank()) {
            return new ResourceDownload(null, null, null, resource.externalUrl());
        }
        return new ResourceDownload(fileStorage.load(resource.storageKey()), resource.contentType(), resource.originalFilename(), null);
    }

    @Transactional
    public AnnouncementView createAnnouncement(long courseId, String title, String content, Boolean top, CurrentUser actor,
                                               String correlationId) {
        requireContentManager(courseId, actor);
        Instant publishedAt = Instant.now();
        CourseRepository.Announcement announcement = courses.createAnnouncement(courseId, announcementTitle(title), announcementContent(content),
                Boolean.TRUE.equals(top), actor.id());
        outbox.append("course.announcement.published.v2", "course-announcement", String.valueOf(announcement.id()), 1, correlationId,
                Map.of("courseId", String.valueOf(courseId), "announcementId", String.valueOf(announcement.id()),
                        "publishedAt", publishedAt.toString()));
        return announcementView(announcement);
    }

    @Transactional
    public AnnouncementView updateAnnouncement(long courseId, long announcementId, String title, String content, Boolean top, CurrentUser actor) {
        requireContentManager(courseId, actor);
        CourseRepository.Announcement current = announcement(courseId, announcementId);
        courses.updateAnnouncement(courseId, announcementId, title == null ? current.title() : announcementTitle(title),
                content == null ? current.content() : announcementContent(content), top == null ? current.top() : top);
        return announcementView(announcement(courseId, announcementId));
    }

    @Transactional
    public void deleteAnnouncement(long courseId, long announcementId, CurrentUser actor) {
        requireContentManager(courseId, actor);
        announcement(courseId, announcementId);
        courses.deleteAnnouncement(courseId, announcementId);
    }

    public List<AnnouncementView> announcements(long courseId, CurrentUser actor) {
        requireMember(courseId, actor);
        return courses.announcements(courseId).stream().map(this::announcementView).toList();
    }

    public AuthorizationDecision authorization(long courseId, long userId, String action) {
        course(courseId);
        CourseRepository.Member member = courses.member(courseId, userId).orElse(null);
        boolean active = member != null && "ACTIVE".equals(member.status());
        boolean manager = active && ("TEACHER".equals(member.role()) || "ASSISTANT".equals(member.role()));
        boolean allowed = switch (action) {
            case "VIEW" -> active;
            case "MANAGE", "LIST_MEMBERS" -> manager;
            case "MANAGE_GRADE" -> active && "TEACHER".equals(member.role());
            default -> throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_ACTION_INVALID", "course action is invalid", false);
        };
        return new AuthorizationDecision(allowed, String.valueOf(courseId), String.valueOf(userId), action, member == null ? 1 : member.memberVersion());
    }

    public MemberPage memberPage(long courseId, String role, String status, int page, int size) {
        course(courseId);
        if (page < 0 || size < 1 || size > 100) throw new CourseException(HttpStatus.BAD_REQUEST, "PAGE_INVALID", "page and size are invalid", false);
        String nextRole = role == null ? null : memberRole(role);
        String nextStatus = status == null ? null : memberStatus(status);
        List<MemberView> items = courses.members(courseId, nextRole, nextStatus, page, size).stream().map(this::memberView).toList();
        return new MemberPage(items, page, size, courses.memberCount(courseId, nextRole, nextStatus));
    }

    public MemberPage members(long courseId, String role, String status, int page, int size, CurrentUser actor) {
        requireMember(courseId, actor);
        return memberPage(courseId, role, status, page, size);
    }

    public MemberPage students(long courseId, int page, int size, CurrentUser actor) {
        requireContentManager(courseId, actor);
        return memberPage(courseId, "STUDENT", "ACTIVE", page, size);
    }

    /** Course-owned bootstrap; Learning never asks synchronously for this snapshot. */
    @Transactional
    public void publishBootstrapSnapshots() {
        for (CourseRepository.Course course : courses.allCourses()) {
            CourseRepository.Course canonical = courses.ensureCanonicalRosterVersion(course.id());
            if (!reconciliation.hasEmitted(canonical.id(), canonical.rosterVersion())) {
                appendRosterSnapshot(canonical.id(), canonical.rosterVersion(), "00000000-0000-0000-0000-000000000000");
            }
        }
    }

    /**
     * Course's own durable repair trigger for a lost Learning projection.  It
     * is intentionally driven only by a previously published Course roster,
     * never by a Learning callback or an arbitrary member event.
     */
    @Transactional
    public int reconcilePublishedRosters() {
        Instant now = Instant.now();
        int reconciled = 0;
        for (long courseId : outbox.publishedRosterCourseIds()) {
            course(courseId);
            if (reconciliation.claimDue(courseId, now, now.plus(RECONCILIATION_INTERVAL))) {
                appendRosterSnapshot(courseId, courses.advanceRoster(courseId), UUID.randomUUID().toString());
                reconciled++;
            }
        }
        return reconciled;
    }

    private void writeMemberFacts(CourseRepository.Member member, String correlationId) {
        outbox.append("course.member.changed.v2", "course-member", member.courseId() + ":" + member.userId(), member.memberVersion(), correlationId,
                Map.of("courseId", String.valueOf(member.courseId()), "userId", String.valueOf(member.userId()),
                        "membershipStatus", "ACTIVE".equals(member.status()) ? "ACTIVE" : "REMOVED", "memberVersion", member.memberVersion()));
    }

    private void writeRosterSnapshot(long courseId, String correlationId) {
        appendRosterSnapshot(courseId, courses.advanceRoster(courseId), correlationId);
    }

    private void appendRosterSnapshot(long courseId, long rosterVersion, String correlationId) {
        List<Map<String, Object>> members = courses.members(courseId).stream().map(member -> Map.<String, Object>of(
                "userId", String.valueOf(member.userId()), "membershipStatus", "ACTIVE".equals(member.status()) ? "ACTIVE" : "REMOVED",
                "memberVersion", member.memberVersion())).toList();
        String eventId = outbox.append("course.membership.snapshot.v2", "course-membership-roster", String.valueOf(courseId), rosterVersion, correlationId,
                Map.of("courseId", String.valueOf(courseId), "rosterVersion", rosterVersion, "members", members));
        reconciliation.record(courseId, eventId, rosterVersion, Instant.now().plus(RECONCILIATION_INTERVAL));
    }

    private CourseRepository.Course course(long courseId) {
        return courses.findCourse(courseId).orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "course does not exist", false));
    }
    private CourseRepository.Resource resource(long courseId, long resourceId) {
        return courses.resource(courseId, resourceId).orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "resource does not exist", false));
    }
    private CourseRepository.Announcement announcement(long courseId, long announcementId) {
        return courses.announcement(courseId, announcementId).orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND", "announcement does not exist", false));
    }
    private void requireTeacher(CurrentUser user) { if (!user.hasRole("TEACHER") && !user.hasRole("ADMIN")) throw forbidden(); }
    private void requireOwner(long courseId, CurrentUser user) {
        if (user.hasRole("ADMIN")) return;
        CourseRepository.Member member = courses.member(courseId, user.id()).orElseThrow(this::forbidden);
        if (!("ACTIVE".equals(member.status()) && "TEACHER".equals(member.role()))) throw forbidden();
    }
    private void requireContentManager(long courseId, CurrentUser user) { if (!canManageContent(courseId, user)) throw forbidden(); }
    private boolean canManageContent(long courseId, CurrentUser user) {
        if (user.hasRole("ADMIN")) return true;
        return courses.member(courseId, user.id()).filter(member -> "ACTIVE".equals(member.status()))
                .map(member -> "TEACHER".equals(member.role()) || "ASSISTANT".equals(member.role())).orElse(false);
    }
    private void requireMember(long courseId, CurrentUser user) { if (!user.hasRole("ADMIN") && !isMember(courseId, user.id())) throw forbidden(); }
    private boolean isMember(long courseId, long userId) { return courses.member(courseId, userId).map(value -> "ACTIVE".equals(value.status())).orElse(false); }
    private boolean canViewCourse(CourseRepository.Course course, CurrentUser actor) {
        return actor.hasRole("ADMIN") || isMember(course.id(), actor.id()) || ("PUBLIC".equals(course.enrollmentMode()) && "ACTIVE".equals(course.status()));
    }
    private boolean canListCourse(CourseRepository.Course course, CurrentUser actor) {
        return actor.hasRole("ADMIN") || isMember(course.id(), actor.id()) || ("PUBLIC".equals(course.enrollmentMode()) && "ACTIVE".equals(course.status()));
    }
    private CourseException forbidden() { return new CourseException(HttpStatus.FORBIDDEN, "COURSE_ACCESS_FORBIDDEN", "course access is forbidden", false); }

    private String courseName(String value) { return bounded(value, 1, 100, "COURSE_NAME_INVALID", "course name"); }
    private String description(String value) { return value == null ? "" : bounded(value, 0, 2000, "COURSE_DESCRIPTION_INVALID", "course description"); }
    private String chapterTitle(String value) { return bounded(value, 1, 255, "CHAPTER_TITLE_INVALID", "chapter title"); }
    private String objective(String value) { return value == null ? "" : bounded(value, 0, 4000, "CHAPTER_OBJECTIVE_INVALID", "chapter objective"); }
    private String resourceTitle(String value) { return bounded(value, 1, 255, "RESOURCE_INVALID", "resource title"); }
    private String announcementTitle(String value) { return bounded(value, 1, 200, "ANNOUNCEMENT_INVALID", "announcement title"); }
    private String announcementContent(String value) { return bounded(value, 1, 16000, "ANNOUNCEMENT_INVALID", "announcement content"); }
    private String bounded(String value, int min, int max, String code, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) throw new CourseException(HttpStatus.BAD_REQUEST, code, field + " has invalid length", false);
        return trimmed;
    }
    private String enrollmentMode(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PUBLIC", "INVITE", "REVIEW").contains(result)) throw new CourseException(HttpStatus.BAD_REQUEST, "ENROLLMENT_MODE_INVALID", "unsupported enrollment mode", false);
        return result;
    }
    private String courseStatus(String value) {
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "DRAFT", "ARCHIVED", "CLOSED").contains(result)) throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_STATUS_INVALID", "unsupported course status", false);
        return result;
    }
    private String inviteCode(String mode, String requested, String current) {
        if (!"INVITE".equals(mode)) return null;
        String candidate = requested == null || requested.isBlank() ? current : requested.trim();
        if (candidate == null || candidate.isBlank()) candidate = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (candidate.length() > 64) throw new CourseException(HttpStatus.BAD_REQUEST, "INVITE_CODE_INVALID", "invite code is invalid", false);
        return candidate;
    }
    private Integer capacity(Integer value) {
        if (value == null) return null;
        if (value < 1 || value > 10000) throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_CAPACITY_INVALID", "course capacity is invalid", false);
        return value;
    }
    private int order(Integer value) {
        int result = value == null ? 0 : value;
        if (result < 0 || result > 100000) throw new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_ORDER_INVALID", "chapter sort order is invalid", false);
        return result;
    }
    private Long validateParent(long courseId, Long chapterId, String parentId) {
        if (parentId == null || parentId.isBlank()) return null;
        long parent;
        try { parent = Long.parseLong(parentId); } catch (NumberFormatException invalid) { throw new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_PARENT_INVALID", "chapter parent is invalid", false); }
        if (chapterId != null && parent == chapterId) throw new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_PARENT_INVALID", "chapter cannot parent itself", false);
        CourseRepository.Chapter current = courses.chapter(courseId, parent).orElseThrow(() -> new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_PARENT_INVALID", "chapter parent does not exist", false));
        if (chapterId != null) {
            while (current.parentId() != null) {
                if (current.parentId() == chapterId) throw new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_PARENT_INVALID", "chapter hierarchy cannot contain a cycle", false);
                current = courses.chapter(courseId, current.parentId()).orElseThrow(() -> new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_PARENT_INVALID", "chapter hierarchy is invalid", false));
            }
        }
        return parent;
    }
    private Long validateResourceChapter(long courseId, String chapterId) { return validateParent(courseId, null, chapterId); }
    private String resourceType(String value) {
        String type = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("LINK", "DOCUMENT", "COURSEWARE", "VIDEO", "IMAGE", "ARCHIVE").contains(type)) throw new CourseException(HttpStatus.BAD_REQUEST, "RESOURCE_TYPE_INVALID", "resource type is invalid", false);
        return type;
    }
    private String resourceTypeFromFilename(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "COURSEWARE";
        if (lower.endsWith(".mp4")) return "VIDEO";
        if (lower.matches(".*\\.(png|jpg|jpeg|gif)$")) return "IMAGE";
        if (lower.matches(".*\\.(zip|rar)$")) return "ARCHIVE";
        return "DOCUMENT";
    }
    private String resourceVisibility(String value, String fallback) {
        String visibility = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("STUDENT", "TEACHER").contains(visibility)) throw new CourseException(HttpStatus.BAD_REQUEST, "RESOURCE_VISIBILITY_INVALID", "resource visibility is invalid", false);
        return visibility;
    }
    private String resourceUrl(String value) {
        String url = bounded(value, 1, 1024, "RESOURCE_INVALID", "resource URL");
        try {
            URI parsed = URI.create(url);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) && !"http".equalsIgnoreCase(parsed.getScheme())) throw new IllegalArgumentException();
            return url;
        } catch (IllegalArgumentException invalid) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "RESOURCE_INVALID", "resource URL must be HTTP(S)", false);
        }
    }
    private String memberRole(String value) {
        String role = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("STUDENT", "ASSISTANT", "TEACHER").contains(role)) throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_INVALID", "member role is invalid", false);
        return role;
    }
    private String memberStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "PENDING", "REMOVED", "REJECTED").contains(status)) throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_INVALID", "member status is invalid", false);
        return status;
    }
    private String joinMethod(String mode) { return switch (mode) { case "INVITE" -> "INVITE"; case "REVIEW" -> "REVIEW"; default -> "PUBLIC"; }; }
    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private CourseView view(CourseRepository.Course course, CurrentUser viewer) {
        boolean manager = canManageContent(course.id(), viewer);
        return new CourseView(String.valueOf(course.id()), course.name(), course.description(), String.valueOf(course.teacherId()), course.enrollmentMode(), course.status(),
                isMember(course.id(), viewer.id()), manager ? course.inviteCode() : null, course.maxStudents());
    }
    private MemberView memberView(CourseRepository.Member member) { return new MemberView(String.valueOf(member.userId()), member.role(), member.status(), member.memberVersion(), member.joinMethod()); }
    private ChapterView chapterView(CourseRepository.Chapter chapter) { return new ChapterView(chapter.id(), chapter.courseId(), chapter.title(), chapter.parentId(), chapter.sortOrder(), chapter.objective(), chapter.visible(), chapter.chapterType()); }
    private ResourceView resourceView(CourseRepository.Resource resource) {
        String url = resource.externalUrl() == null || resource.externalUrl().isBlank()
                ? "/api/v1/courses/" + resource.courseId() + "/resources/" + resource.id() + "/download" : resource.externalUrl();
        return new ResourceView(resource.id(), resource.courseId(), resource.chapterId(), resource.name(), url, resource.type(), resource.visibility(), resource.publishAt(), resource.version(), resource.downloadCount());
    }
    private AnnouncementView announcementView(CourseRepository.Announcement announcement) { return new AnnouncementView(announcement.id(), announcement.courseId(), announcement.title(), announcement.content(), announcement.top(), announcement.publisherId()); }

    public record CourseView(String id, String name, String description, String teacherId, String enrollmentMode, String status,
                             boolean member, String inviteCode, Integer maxStudents) { }
    public record CoursePage(List<CourseView> items, int page, int size, int total) { }
    public record MemberView(String userId, String role, String status, long memberVersion, String joinMethod) { }
    public record ChapterView(long id, long courseId, String title, Long parentId, int sortOrder, String objective, boolean visible, int chapterType) { }
    public record ResourceView(long id, long courseId, Long chapterId, String title, String url, String type, String visibility,
                               LocalDateTime publishAt, int version, long downloadCount) { }
    public record ResourceDownload(byte[] content, String contentType, String filename, String redirectUrl) { }
    public record AnnouncementView(long id, long courseId, String title, String content, boolean top, long publisherId) { }
    public record AuthorizationDecision(boolean allowed, String courseId, String userId, String action, long memberVersion) { }
    public record MemberPage(List<MemberView> items, int page, int size, long total) { }
}
