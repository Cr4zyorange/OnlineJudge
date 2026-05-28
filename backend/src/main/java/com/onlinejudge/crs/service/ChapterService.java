package com.onlinejudge.crs.service;

import com.onlinejudge.common.exception.BusinessException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.crs.domain.Chapter;
import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.dto.ChapterCreateRequest;
import com.onlinejudge.crs.domain.dto.ChapterResponse;
import com.onlinejudge.crs.domain.dto.ChapterUpdateRequest;
import com.onlinejudge.crs.mapper.ChapterRepository;
import com.onlinejudge.crs.mapper.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChapterService {
    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;

    public ChapterService(ChapterRepository chapterRepository, CourseRepository courseRepository) {
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public ChapterResponse create(Long courseId, ChapterCreateRequest request, CurrentUser user) {
        requireManagePermission(courseId, user);
        validateParent(courseId, request.parentId());
        int orderNum = request.orderNum() == null ? chapterRepository.nextOrder(courseId, request.parentId()) : request.orderNum();
        return toResponse(chapterRepository.insert(courseId, request, orderNum), List.of());
    }

    public List<ChapterResponse> tree(Long courseId, CurrentUser user) {
        requireViewPermission(courseId, user);
        return buildTree(chapterRepository.listByCourse(courseId));
    }

    @Transactional
    public ChapterResponse update(Long courseId, Long chapterId, ChapterUpdateRequest request, CurrentUser user) {
        requireManagePermission(courseId, user);
        Chapter chapter = getChapter(courseId, chapterId);
        if (chapterId.equals(request.parentId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "章节不能作为自己的父章节");
        }
        validateParent(courseId, request.parentId());
        ensureNoCycle(courseId, chapterId, request.parentId());
        int orderNum = request.orderNum() == null ? chapter.orderNum() : request.orderNum();
        return toResponse(chapterRepository.update(courseId, chapterId, request, orderNum), List.of());
    }

    @Transactional
    public void delete(Long courseId, Long chapterId, CurrentUser user) {
        requireManagePermission(courseId, user);
        getChapter(courseId, chapterId);
        chapterRepository.deleteWithDescendants(courseId, chapterId);
    }

    private void requireViewPermission(Long courseId, CurrentUser user) {
        getCourse(courseId);
        if (isAdmin(user) || isActiveMember(courseId, user.id())) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
    }

    private void requireManagePermission(Long courseId, CurrentUser user) {
        getCourse(courseId);
        if (isAdmin(user)) {
            return;
        }
        CourseMember member = courseRepository.findMember(courseId, user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "无权限访问"));
        if (member.status() != CourseMemberStatus.ACTIVE || member.role() != CourseMemberRole.TEACHER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
        }
    }

    private void getCourse(Long courseId) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "课程不存在"));
    }

    private Chapter getChapter(Long courseId, Long chapterId) {
        return chapterRepository.findById(courseId, chapterId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "章节不存在"));
    }

    private void validateParent(Long courseId, Long parentId) {
        if (parentId == null) {
            return;
        }
        getChapter(courseId, parentId);
    }

    private void ensureNoCycle(Long courseId, Long chapterId, Long parentId) {
        Long cursor = parentId;
        while (cursor != null) {
            if (chapterId.equals(cursor)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "章节层级不能形成循环");
            }
            cursor = getChapter(courseId, cursor).parentId();
        }
    }

    private boolean isActiveMember(Long courseId, Long userId) {
        return courseRepository.findMember(courseId, userId)
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                .isPresent();
    }

    private boolean isAdmin(CurrentUser user) {
        return user.hasRole("ADMIN");
    }

    private List<ChapterResponse> buildTree(List<Chapter> chapters) {
        Map<Long, MutableChapter> nodes = new LinkedHashMap<>();
        for (Chapter chapter : chapters) {
            nodes.put(chapter.id(), new MutableChapter(chapter));
        }
        List<MutableChapter> roots = new ArrayList<>();
        for (MutableChapter node : nodes.values()) {
            if (node.chapter.parentId() == null || !nodes.containsKey(node.chapter.parentId())) {
                roots.add(node);
            } else {
                nodes.get(node.chapter.parentId()).children.add(node);
            }
        }
        return roots.stream()
                .sorted(chapterComparator())
                .map(this::toResponse)
                .toList();
    }

    private ChapterResponse toResponse(MutableChapter node) {
        return toResponse(node.chapter, node.children.stream()
                .sorted(chapterComparator())
                .map(this::toResponse)
                .toList());
    }

    private ChapterResponse toResponse(Chapter chapter, List<ChapterResponse> children) {
        return new ChapterResponse(
                chapter.id(),
                chapter.courseId(),
                chapter.parentId(),
                chapter.title(),
                chapter.content(),
                chapter.orderNum(),
                children,
                chapter.createdAt(),
                chapter.updatedAt()
        );
    }

    private Comparator<MutableChapter> chapterComparator() {
        return Comparator.comparing((MutableChapter node) -> node.chapter.orderNum())
                .thenComparing(node -> node.chapter.id());
    }

    private static class MutableChapter {
        private final Chapter chapter;
        private final List<MutableChapter> children = new ArrayList<>();

        private MutableChapter(Chapter chapter) {
            this.chapter = chapter;
        }
    }
}
