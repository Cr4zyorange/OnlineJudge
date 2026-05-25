package com.onlinejudge.lab.controller;

import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.service.LabExperimentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LabExperimentController {
    private final LabExperimentService labExperimentService;

    public LabExperimentController(LabExperimentService labExperimentService) {
        this.labExperimentService = labExperimentService;
    }

    @PostMapping("/courses/{courseId}/labs")
    public ResponseEntity<ApiResponse<LabExperiment>> createLab(
            @PathVariable long courseId,
            CurrentUser currentUser,
            @Valid @RequestBody CreateLabExperimentRequest request
    ) {
        requireTeacher(currentUser);
        LabExperiment created = labExperimentService.createLab(courseId, currentUser.id(), request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @GetMapping("/courses/{courseId}/labs")
    public ApiResponse<List<LabExperiment>> listLabs(
            @PathVariable long courseId,
            CurrentUser currentUser,
            @RequestParam(required = false) LabExperimentStatus status
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(labExperimentService.listLabs(courseId, currentUser.id(), status));
    }

    @GetMapping("/labs/{labId}")
    public ApiResponse<LabExperiment> getLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(labExperimentService.getLab(labId, currentUser.id()));
    }

    @PutMapping("/labs/{labId}")
    public ApiResponse<LabExperiment> updateLab(
            @PathVariable long labId,
            CurrentUser currentUser,
            @Valid @RequestBody UpdateLabExperimentRequest request
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(labExperimentService.updateLab(labId, currentUser.id(), request.toCommand()));
    }

    @DeleteMapping("/labs/{labId}")
    public ApiResponse<LabExperiment> deleteLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(labExperimentService.deleteLab(labId, currentUser.id()));
    }

    @PostMapping("/labs/{labId}/publish")
    public ApiResponse<LabExperiment> publishLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(labExperimentService.publishLab(labId, currentUser.id()));
    }

    @PostMapping("/labs/{labId}/close")
    public ApiResponse<LabExperiment> closeLab(
            @PathVariable long labId,
            CurrentUser currentUser
    ) {
        requireTeacher(currentUser);
        return ApiResponse.ok(labExperimentService.closeLab(labId, currentUser.id()));
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (!currentUser.hasRole("TEACHER") && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("教师无实验管理权限");
        }
    }
}
