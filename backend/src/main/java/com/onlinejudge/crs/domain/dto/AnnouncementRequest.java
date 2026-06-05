package com.onlinejudge.crs.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnouncementRequest(
        @NotBlank(message = "公告标题不能为空") @Size(max = 200, message = "公告标题不能超过200个字符") String title,
        @NotBlank(message = "公告内容不能为空") @Size(max = 5000, message = "公告内容超过最大长度") String content,
        Boolean isTop
) {
}
