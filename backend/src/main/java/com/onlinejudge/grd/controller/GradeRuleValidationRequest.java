package com.onlinejudge.grd.controller;

import com.onlinejudge.grd.domain.CreateGradeItemCommand;
import jakarta.validation.Valid;

import java.util.List;

public record GradeRuleValidationRequest(
        @Valid
        List<CreateGradeItemRequest> gradeItems
) {
    public List<CreateGradeItemCommand> toCommands() {
        if (gradeItems == null) {
            return List.of();
        }
        return gradeItems.stream()
                .map(CreateGradeItemRequest::toCommand)
                .toList();
    }
}
