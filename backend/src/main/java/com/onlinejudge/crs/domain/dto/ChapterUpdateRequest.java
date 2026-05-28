package com.onlinejudge.crs.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChapterUpdateRequest(
        Long parentId,
        @NotBlank @Size(max = 255) String chapterName,
        @Min(1) Integer sortOrder,
        @Size(max = 2000) String objective,
        @Min(0) @Max(1) Integer visibleStatus,
        @Min(1) @Max(3) Integer chapterType
) {
}
