package com.onlinejudge.courseservice.service;

import com.onlinejudge.courseservice.persistence.CourseOutboxRepository;
import com.onlinejudge.courseservice.persistence.CourseRosterReconciliationRepository;
import com.onlinejudge.courseservice.persistence.CourseRepository;
import com.onlinejudge.courseservice.security.CurrentUser;
import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CourseService {
    private final CourseRepository courses;
    private final CourseOutboxRepository outbox;
    private final CourseRosterReconciliationRepository reconciliation;

    public CourseService(CourseRepository courses, CourseOutboxRepository outbox, CourseRosterReconciliationRepository reconciliation) {
        this.courses = courses;
        this.outbox = outbox;
        this.reconciliation = reconciliation;
    }

    @Transactional
    public CourseView create(String name, String enrollmentMode, CurrentUser actor, String correlationId) {
        requireTeacher(actor);
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_NAME_INVALID", "course name must be 1-100 characters", false);
        }
        String mode = enrollmentMode == null || enrollmentMode.isBlank() ? "PUBLIC" : enrollmentMode.trim().toUpperCase();
        if (!List.of("PUBLIC", "INVITE", "REVIEW").contains(mode)) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "ENROLLMENT_MODE_INVALID", "unsupported enrollment mode", false);
        }
        long courseId = courses.createCourse(name.trim(), actor.id(), mode);
        CourseRepository.Member teacher = courses.insertMember(courseId, actor.id(), "TEACHER", "ACTIVE");
        writeMemberFacts(teacher, correlationId);
        writeRosterSnapshot(courseId, correlationId);
        return view(courses.findCourse(courseId).orElseThrow(), actor);
    }

    @Transactional
    public MemberView join(long courseId, CurrentUser actor, String correlationId) {
        CourseRepository.Course course = course(courseId);
        if (!"ACTIVE".equals(course.status())) {
            throw new CourseException(HttpStatus.CONFLICT, "COURSE_CLOSED", "course is not open for enrollment", false);
        }
        if (courses.member(courseId, actor.id()).isPresent()) {
            throw new CourseException(HttpStatus.CONFLICT, "ALREADY_JOINED", "course membership already exists", false);
        }
        String status = "REVIEW".equals(course.enrollmentMode()) ? "PENDING" : "ACTIVE";
        CourseRepository.Member member = courses.insertMember(courseId, actor.id(), "STUDENT", status);
        writeMemberFacts(member, correlationId);
        writeRosterSnapshot(courseId, correlationId);
        return memberView(member);
    }

    @Transactional
    public MemberView changeMember(long courseId, long userId, String role, String status, CurrentUser actor, String correlationId) {
        requireManager(courseId, actor);
        CourseRepository.Member current = courses.member(courseId, userId)
                .orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "COURSE_MEMBER_NOT_FOUND", "course member does not exist", false));
        String nextRole = role == null || role.isBlank() ? current.role() : role.trim().toUpperCase();
        String nextStatus = status == null || status.isBlank() ? current.status() : status.trim().toUpperCase();
        if (!List.of("STUDENT", "ASSISTANT", "TEACHER").contains(nextRole)
                || !List.of("ACTIVE", "PENDING", "REMOVED", "REJECTED").contains(nextStatus)) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_INVALID", "member role or status is invalid", false);
        }
        if (current.role().equals("TEACHER") && "ACTIVE".equals(current.status())
                && (!"TEACHER".equals(nextRole) || !"ACTIVE".equals(nextStatus))
                && courses.activeMemberCount(courseId, "TEACHER") <= 1) {
            throw new CourseException(HttpStatus.CONFLICT, "LAST_TEACHER_REQUIRED", "a course requires one active teacher", false);
        }
        CourseRepository.Member changed = courses.updateMember(courseId, userId, nextRole, nextStatus);
        writeMemberFacts(changed, correlationId);
        writeRosterSnapshot(courseId, correlationId);
        return memberView(changed);
    }

    public CourseView detail(long courseId, CurrentUser actor) {
        CourseRepository.Course course = course(courseId);
        if (!isMember(courseId, actor.id()) && !actor.hasRole("ADMIN")) {
            throw forbidden();
        }
        return view(course, actor);
    }

    public List<CourseView> list(CurrentUser actor) {
        return courses.allCourseIds().stream().map(courses::findCourse).flatMap(java.util.Optional::stream)
                .filter(course -> actor.hasRole("ADMIN") || isMember(course.id(), actor.id()) || course.teacherId() == actor.id())
                .map(course -> view(course, actor)).toList();
    }

    @Transactional
    public ChapterView createChapter(long courseId, String title, String parentId, CurrentUser actor) {
        requireManager(courseId, actor);
        if (title == null || title.isBlank()) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "CHAPTER_TITLE_INVALID", "chapter title is required", false);
        }
        courses.createChapter(courseId, title.trim(), parentId, actor.id());
        CourseRepository.Chapter chapter = courses.chapters(courseId).getLast();
        return new ChapterView(chapter.id(), chapter.courseId(), chapter.title(), chapter.parentId());
    }

    public List<ChapterView> chapters(long courseId, CurrentUser actor) {
        requireMember(courseId, actor);
        return courses.chapters(courseId).stream()
                .map(chapter -> new ChapterView(chapter.id(), chapter.courseId(), chapter.title(), chapter.parentId())).toList();
    }

    @Transactional
    public ResourceView createResource(long courseId, String title, String url, CurrentUser actor) {
        requireManager(courseId, actor);
        if (title == null || title.isBlank() || url == null || url.isBlank()) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "RESOURCE_INVALID", "resource title and URL are required", false);
        }
        courses.createResource(courseId, title.trim(), url.trim(), actor.id());
        CourseRepository.Resource resource = courses.resources(courseId).getLast();
        return new ResourceView(resource.id(), resource.courseId(), resource.title(), resource.url());
    }

    public List<ResourceView> resources(long courseId, CurrentUser actor) {
        requireMember(courseId, actor);
        return courses.resources(courseId).stream()
                .map(resource -> new ResourceView(resource.id(), resource.courseId(), resource.title(), resource.url())).toList();
    }

    public AuthorizationDecision authorization(long courseId, long userId, String action) {
        course(courseId);
        CourseRepository.Member member = courses.member(courseId, userId).orElse(null);
        boolean active = member != null && "ACTIVE".equals(member.status());
        boolean allowed = switch (action) {
            case "VIEW" -> active;
            case "MANAGE", "MANAGE_GRADE", "LIST_MEMBERS" -> active && "TEACHER".equals(member.role());
            default -> throw new CourseException(HttpStatus.BAD_REQUEST, "COURSE_ACTION_INVALID", "course action is invalid", false);
        };
        return new AuthorizationDecision(allowed, String.valueOf(courseId), String.valueOf(userId), action,
                member == null ? 1 : member.memberVersion());
    }

    public MemberPage memberPage(long courseId, String role, int page, int size) {
        course(courseId);
        if (page < 0 || size < 1 || size > 100) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "PAGE_INVALID", "page and size are invalid", false);
        }
        List<MemberView> items = courses.members(courseId, role == null ? null : role.toUpperCase(), page, size)
                .stream().map(this::memberView).toList();
        return new MemberPage(items, page, size, courses.activeMemberCount(courseId, role == null ? null : role.toUpperCase()));
    }

    public MemberPage members(long courseId, String role, int page, int size, CurrentUser actor) {
        requireMember(courseId, actor);
        return memberPage(courseId, role, page, size);
    }

    /** Used at bootstrap/reconciliation: the snapshot alone proves a complete roster watermark to Learning. */
    @Transactional
    public void publishBootstrapSnapshots() {
        for (Long courseId : courses.allCourseIds()) {
            CourseRepository.Course course = courses.ensureCanonicalRosterVersion(courseId);
            if (!reconciliation.hasEmitted(courseId, course.rosterVersion())) {
                appendRosterSnapshot(courseId, course.rosterVersion(), "00000000-0000-0000-0000-000000000000");
            }
        }
    }

    private void writeMemberFacts(CourseRepository.Member member, String correlationId) {
        outbox.append("course.member.changed.v2", "course-member", member.courseId() + ":" + member.userId(),
                member.memberVersion(), correlationId, Map.of(
                        "courseId", String.valueOf(member.courseId()),
                        "userId", String.valueOf(member.userId()),
                        "membershipStatus", "ACTIVE".equals(member.status()) ? "ACTIVE" : "REMOVED",
                        "memberVersion", member.memberVersion()));
    }

    private void writeRosterSnapshot(long courseId, String correlationId) {
        long rosterVersion = courses.advanceRoster(courseId);
        appendRosterSnapshot(courseId, rosterVersion, correlationId);
    }

    private void appendRosterSnapshot(long courseId, long rosterVersion, String correlationId) {
        List<Map<String, Object>> members = courses.members(courseId).stream().map(member -> Map.<String, Object>of(
                "userId", String.valueOf(member.userId()),
                "membershipStatus", "ACTIVE".equals(member.status()) ? "ACTIVE" : "REMOVED",
                "memberVersion", member.memberVersion())).toList();
        outbox.append("course.membership.snapshot.v2", "course-membership-roster", String.valueOf(courseId), rosterVersion,
                correlationId, Map.of("courseId", String.valueOf(courseId), "rosterVersion", rosterVersion, "members", members));
        reconciliation.record(courseId, rosterVersion);
    }

    private CourseRepository.Course course(long courseId) {
        return courses.findCourse(courseId).orElseThrow(() -> new CourseException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "course does not exist", false));
    }
    private void requireTeacher(CurrentUser user) { if (!user.hasRole("TEACHER") && !user.hasRole("ADMIN")) throw forbidden(); }
    private void requireManager(long courseId, CurrentUser user) {
        if (user.hasRole("ADMIN")) return;
        CourseRepository.Member member = courses.member(courseId, user.id()).orElseThrow(this::forbidden);
        if (!("ACTIVE".equals(member.status()) && "TEACHER".equals(member.role()))) throw forbidden();
    }
    private void requireMember(long courseId, CurrentUser user) { if (!isMember(courseId, user.id()) && !user.hasRole("ADMIN")) throw forbidden(); }
    private boolean isMember(long courseId, long userId) { return courses.member(courseId, userId).map(value -> "ACTIVE".equals(value.status())).orElse(false); }
    private CourseException forbidden() { return new CourseException(HttpStatus.FORBIDDEN, "COURSE_ACCESS_FORBIDDEN", "course access is forbidden", false); }
    private CourseView view(CourseRepository.Course course, CurrentUser viewer) { return new CourseView(String.valueOf(course.id()), course.name(), String.valueOf(course.teacherId()), course.enrollmentMode(), course.status(), isMember(course.id(), viewer.id())); }
    private MemberView memberView(CourseRepository.Member member) { return new MemberView(String.valueOf(member.userId()), member.role(), member.status(), member.memberVersion()); }

    public record CourseView(String id, String name, String teacherId, String enrollmentMode, String status, boolean member) { }
    public record MemberView(String userId, String role, String status, long memberVersion) { }
    public record ChapterView(long id, long courseId, String title, Long parentId) { }
    public record ResourceView(long id, long courseId, String title, String url) { }
    public record AuthorizationDecision(boolean allowed, String courseId, String userId, String action, long memberVersion) { }
    public record MemberPage(List<MemberView> items, int page, int size, long total) { }
}
