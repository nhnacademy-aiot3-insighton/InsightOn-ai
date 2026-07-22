package com.insighton.ai.report.dto;

import com.insighton.ai.report.entity.Report;
import com.insighton.ai.report.entity.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "리포트 상세 (본문 포함)")
public record ReportDetailResponse(
        @Schema(description = "리포트 ID", example = "1")
        Long reportId,

        @Schema(description = "그룹 ID", example = "5")
        Long groupId,

        @Schema(description = "위치 ID", example = "42")
        Long locationId,

        @Schema(description = "리포트 종류", example = "WEEKLY")
        ReportType reportType,

        @Schema(description = "본문 내용")
        String content,

        @Schema(description = "생성일시")
        OffsetDateTime createdAt

) {
    public static ReportDetailResponse from(Report report) {
        return new ReportDetailResponse(
                report.getReportId(),
                report.getGroupId(),
                report.getLocationId(),
                report.getReportType(),
                report.getContent(),
                report.getCreatedAt()
        );
    }
}
