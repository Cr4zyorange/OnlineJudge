package com.onlinejudge.crs.controller;

import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.crs.domain.dto.ChapterResponse;
import com.onlinejudge.crs.domain.dto.ChapterUpdateRequest;
import com.onlinejudge.crs.service.ChapterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chapters")
public class ChapterController {
    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @PutMapping("/{chapterId}")
    public ApiResponse<ChapterResponse> update(@PathVariable Long chapterId,
                                               @Valid @RequestBody ChapterUpdateRequest request,
                                               CurrentUser currentUser) {
        return ApiResponse.ok(chapterService.update(chapterId, request, currentUser));
    }

    @DeleteMapping("/{chapterId}")
    public ApiResponse<Void> delete(@PathVariable Long chapterId, CurrentUser currentUser) {
        chapterService.delete(chapterId, currentUser);
        return ApiResponse.ok(null);
    }
}
