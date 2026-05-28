package com.onlinejudge.crs.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChapterUpdateRequest(
        Long parentId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String content,
        @Min(0) Integer orderNum
) {
}
