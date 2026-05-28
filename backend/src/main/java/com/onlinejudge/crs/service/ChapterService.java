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
import java.util.Optional;

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
        int sortOrder = request.sortOrder() == null ? chapterRepository.nextOrder(courseId, request.parentId()) : request.sortOrder();
        Chapter chapter = chapterRepository.insert(courseId, request, sortOrder);
        reorderSiblings(courseId, request.parentId(), chapter.id(), sortOrder);
        return toResponse(getChapter(courseId, chapter.id()), List.of());
    }

    public List<ChapterResponse> tree(Long courseId, CurrentUser user) {
        requireViewPermission(courseId, user);
        return buildTree(chapterRepository.listByCourse(courseId), canManageCourse(courseId, user));
    }

    @Transactional
    public ChapterResponse update(Long chapterId, ChapterUpdateRequest request, CurrentUser user) {
        Chapter chapter = getChapter(chapterId);
        Long courseId = chapter.courseId();
        requireManagePermission(courseId, user);
        if (chapterId.equals(request.parentId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "章节不能作为自己的父章节");
        }
        validateParent(courseId, request.parentId());
        ensureNoCycle(courseId, chapterId, request.parentId());
        Long previousParentId = chapter.parentId();
        boolean parentChanged = !sameParent(previousParentId, request.parentId());
        int sortOrder = request.sortOrder() == null
                ? (parentChanged ? chapterRepository.nextOrder(courseId, request.parentId()) : chapter.sortOrder())
                : request.sortOrder();
        chapterRepository.update(courseId, chapterId, request, sortOrder);
        if (parentChanged) {
            normalizeSiblings(courseId, previousParentId);
        }
        reorderSiblings(courseId, request.parentId(), chapterId, sortOrder);
        return toResponse(getChapter(courseId, chapterId), List.of());
    }

    @Transactional
    public void delete(Long chapterId, CurrentUser user) {
        Chapter chapter = getChapter(chapterId);
        Long courseId = chapter.courseId();
        requireManagePermission(courseId, user);
        chapterRepository.deleteWithDescendants(courseId, chapterId);
        normalizeSiblings(courseId, chapter.parentId());
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

    private Chapter getChapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
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

    private boolean canManageCourse(Long courseId, CurrentUser user) {
        if (isAdmin(user)) {
            return true;
        }
        return courseRepository.findMember(courseId, user.id())
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                .filter(member -> member.role() == CourseMemberRole.TEACHER)
                .isPresent();
    }

    private boolean isAdmin(CurrentUser user) {
        return user.hasRole("ADMIN");
    }

    private List<ChapterResponse> buildTree(List<Chapter> chapters, boolean includeHidden) {
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
                .flatMap(node -> toResponse(node, includeHidden).stream())
                .toList();
    }

    private Optional<ChapterResponse> toResponse(MutableChapter node, boolean includeHidden) {
        if (!includeHidden && node.chapter.visibleStatus() != 1) {
            return Optional.empty();
        }
        return Optional.of(toResponse(node.chapter, node.children.stream()
                .sorted(chapterComparator())
                .flatMap(child -> toResponse(child, includeHidden).stream())
                .toList()));
    }

    private ChapterResponse toResponse(Chapter chapter, List<ChapterResponse> children) {
        return new ChapterResponse(
                chapter.id(),
                chapter.courseId(),
                chapter.parentId(),
                chapter.chapterName(),
                chapter.sortOrder(),
                chapter.objective(),
                chapter.visibleStatus(),
                chapter.chapterType(),
                children,
                chapter.createdAt(),
                chapter.updatedAt()
        );
    }

    private Comparator<MutableChapter> chapterComparator() {
        return Comparator.comparing((MutableChapter node) -> node.chapter.sortOrder())
                .thenComparing(node -> node.chapter.id());
    }

    private void reorderSiblings(Long courseId, Long parentId, Long targetChapterId, int targetSortOrder) {
        List<Chapter> siblings = new ArrayList<>(chapterRepository.listSiblings(courseId, parentId));
        Chapter target = siblings.stream()
                .filter(chapter -> chapter.id().equals(targetChapterId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "章节不存在"));
        siblings.removeIf(chapter -> chapter.id().equals(targetChapterId));
        int index = Math.max(0, Math.min(targetSortOrder - 1, siblings.size()));
        siblings.add(index, target);
        persistSiblingOrder(courseId, siblings);
    }

    private void normalizeSiblings(Long courseId, Long parentId) {
        persistSiblingOrder(courseId, chapterRepository.listSiblings(courseId, parentId));
    }

    private void persistSiblingOrder(Long courseId, List<Chapter> siblings) {
        for (int index = 0; index < siblings.size(); index++) {
            int nextOrder = index + 1;
            if (!Integer.valueOf(nextOrder).equals(siblings.get(index).sortOrder())) {
                chapterRepository.updateSortOrder(courseId, siblings.get(index).id(), nextOrder);
            }
        }
    }

    private boolean sameParent(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private static class MutableChapter {
        private final Chapter chapter;
        private final List<MutableChapter> children = new ArrayList<>();

        private MutableChapter(Chapter chapter) {
            this.chapter = chapter;
        }
    }
}
