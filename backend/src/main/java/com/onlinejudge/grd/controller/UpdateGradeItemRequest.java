package com.onlinejudge.grd.controller;

import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.grd.domain.UpdateGradeItemCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateGradeItemRequest(
        @NotBlank(message = "成绩项名称不能为空")
        String name,
        @NotNull(message = "来源类型不支持")
        SourceType sourceType,
        Long sourceId,
        @NotNull(message = "满分值必须大于 0")
        @Positive(message = "满分值必须大于 0")
        BigDecimal fullScore,
        @NotNull(message = "权重必须在 0 到 1 之间")
        @DecimalMin(value = "0.0", message = "权重必须在 0 到 1 之间")
        @DecimalMax(value = "1.0", message = "权重必须在 0 到 1 之间")
        BigDecimal weight,
        boolean includedInFinal,
        int sortOrder,
        Boolean enabled
) {
    public UpdateGradeItemCommand toCommand() {
        return new UpdateGradeItemCommand(
                name,
                sourceType,
                sourceId,
                fullScore,
                weight,
                includedInFinal,
                sortOrder,
                enabled
        );
    }
}
