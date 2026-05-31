package com.onlinejudge.crs.service;

import com.onlinejudge.common.exception.BusinessException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import com.onlinejudge.crs.domain.Chapter;
import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.CourseResource;
import com.onlinejudge.crs.domain.ResourceType;
import com.onlinejudge.crs.domain.ResourceVisibility;
import com.onlinejudge.crs.domain.dto.ResourceResponse;
import com.onlinejudge.crs.domain.dto.ResourceUpdateRequest;
import com.onlinejudge.crs.mapper.ChapterRepository;
import com.onlinejudge.crs.mapper.CourseRepository;
import com.onlinejudge.crs.mapper.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class ResourceService {
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx",
            "txt", "md", "zip", "rar", "png", "jpg", "jpeg", "gif", "mp4"
    );

    private final ResourceRepository resourceRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final FileStorageService fileStorageService;

    public ResourceService(ResourceRepository resourceRepository, CourseRepository courseRepository,
                           ChapterRepository chapterRepository, FileStorageService fileStorageService) {
        this.resourceRepository = resourceRepository;
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public ResourceResponse upload(Long courseId, MultipartFile file, String name, Long chapterId,
                                   ResourceType resourceType, ResourceVisibility visibility,
                                   LocalDateTime publishAt, CurrentUser user) {
        requireManagePermission(courseId, user);
        validateChapter(courseId, chapterId);
        validateFile(file);
        ResourceType normalizedType = resourceType == null ? inferType(file.getOriginalFilename()) : resourceType;
        ResourceVisibility normalizedVisibility = visibility == null ? ResourceVisibility.STUDENT : visibility;
        try {
            StoredFile storedFile = fileStorageService.store(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream()
            );
            String resourceName = normalizeName(name, storedFile.originalFilename());
            CourseResource resource = resourceRepository.insert(
                    courseId,
                    chapterId,
                    resourceName,
                    normalizedType,
                    normalizedVisibility,
                    publishAt,
                    storedFile.storageKey(),
                    storedFile.originalFilename(),
                    storedFile.contentType(),
                    storedFile.size(),
                    user.id()
            );
            return toResponse(resource);
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败，请重试");
        }
    }

    public java.util.List<ResourceResponse> list(Long courseId, CurrentUser user) {
        requireViewPermission(courseId, user);
        boolean teacherView = canManageCourse(courseId, user);
        return resourceRepository.listByCourse(courseId, teacherView).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ResourceResponse update(Long courseId, Long resourceId, ResourceUpdateRequest request, CurrentUser user) {
        requireManagePermission(courseId, user);
        CourseResource resource = getResource(courseId, resourceId);
        validateChapter(courseId, request.chapterId());
        return toResponse(resourceRepository.update(courseId, resource.id(), request));
    }

    @Transactional
    public void delete(Long courseId, Long resourceId, CurrentUser user) {
        requireManagePermission(courseId, user);
        CourseResource resource = getResource(courseId, resourceId);
        resourceRepository.delete(courseId, resourceId);
        fileStorageService.delete(resource.storageKey());
    }

    public ResourceDownload download(Long courseId, Long resourceId, CurrentUser user) {
        requireViewPermission(courseId, user);
        CourseResource resource = getResource(courseId, resourceId);
        if (!canManageCourse(courseId, user) && !isVisibleToStudent(resource)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
        }
        StoredFile storedFile = fileStorageService.load(resource.storageKey());
        return new ResourceDownload(storedFile.resource(), resource.originalFilename(), resource.contentType(), resource.fileSize());
    }

    private CourseResource getResource(Long courseId, Long resourceId) {
        return resourceRepository.findById(courseId, resourceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "资源不存在"));
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

    private boolean canManageCourse(Long courseId, CurrentUser user) {
        if (isAdmin(user)) {
            return true;
        }
        return courseRepository.findMember(courseId, user.id())
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                .filter(member -> member.role() == CourseMemberRole.TEACHER)
                .isPresent();
    }

    private boolean isActiveMember(Long courseId, Long userId) {
        return courseRepository.findMember(courseId, userId)
                .filter(member -> member.status() == CourseMemberStatus.ACTIVE)
                .isPresent();
    }

    private void getCourse(Long courseId) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "课程不存在"));
    }

    private void validateChapter(Long courseId, Long chapterId) {
        if (chapterId == null) {
            return;
        }
        Chapter ignored = chapterRepository.findById(courseId, chapterId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "章节不存在"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件大小不能超过50MB");
        }
        String extension = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的文件类型");
        }
    }

    private boolean isVisibleToStudent(CourseResource resource) {
        return resource.visibility() == ResourceVisibility.STUDENT
                && (resource.publishAt() == null || !resource.publishAt().isAfter(LocalDateTime.now()));
    }

    private ResourceResponse toResponse(CourseResource resource) {
        return new ResourceResponse(
                resource.id(),
                resource.courseId(),
                resource.chapterId(),
                resource.name(),
                resource.resourceType(),
                resource.visibility(),
                resource.publishAt(),
                resource.originalFilename(),
                resource.contentType(),
                resource.fileSize(),
                resource.uploadUserId(),
                "/api/v1/courses/" + resource.courseId() + "/resources/" + resource.id() + "/download",
                resource.createdAt(),
                resource.updatedAt()
        );
    }

    private ResourceType inferType(String filename) {
        return switch (extension(filename)) {
            case "ppt", "pptx" -> ResourceType.COURSEWARE;
            case "mp4" -> ResourceType.VIDEO;
            case "png", "jpg", "jpeg", "gif" -> ResourceType.IMAGE;
            case "zip", "rar" -> ResourceType.ARCHIVE;
            default -> ResourceType.DOCUMENT;
        };
    }

    private String normalizeName(String name, String fallback) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return fallback == null || fallback.isBlank() ? "course-resource" : fallback;
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isAdmin(CurrentUser user) {
        return user.hasRole("ADMIN");
    }
}
