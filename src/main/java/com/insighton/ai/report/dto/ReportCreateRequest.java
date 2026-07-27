package com.insighton.ai.report.dto;

import com.insighton.ai.report.domain.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "리포트 생성 요청")
public record ReportCreateRequest(
        @Schema(description = "그룹 ID", example = "5")
        @NotNull
        Long groupId,

        @Schema(description = "위치 ID", example = "42")
        @NotNull
        Long locationId,

        @Schema(description = "제목")
        @NotBlank
        String title,

        @Schema(description = "리포트 종류", example = "WEEKLY")
        @NotNull
        ReportType reportType,

        @Schema(description = "마크다운 본문")
        @NotBlank
        String content
) {
}
