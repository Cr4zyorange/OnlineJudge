package com.onlinejudge.crs.domain.dto;

import com.onlinejudge.crs.domain.ResourceType;
import com.onlinejudge.crs.domain.ResourceVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ResourceUpdateRequest(
        Long chapterId,
        @NotBlank @Size(max = 255) String name,
        @NotNull ResourceType resourceType,
        @NotNull ResourceVisibility visibility,
        LocalDateTime publishAt
) {
}
