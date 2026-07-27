package com.insighton.ai.report.dto;

import com.insighton.ai.report.domain.Report;
import com.insighton.ai.report.domain.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "리포트 목록 항목 (본문 제외)")
public record ReportListResponse(

        @Schema(description = "리포트 ID", example = "1")
        Long reportId,

        @Schema(description = "그룹 ID", example = "5")
        Long groupId,

        @Schema(description = "위치 ID", example = "42")
        Long locationId,

        @Schema(description = "제목")
        String title,

        @Schema(description = "리포트 종류", example = "WEEKLY")
        ReportType reportType,

        @Schema(description = "생성일시")
        OffsetDateTime createdAt
) {
    public static ReportListResponse from(Report report) {
        return new ReportListResponse(
                report.getReportId(),
                report.getGroupId(),
                report.getLocationId(),
                report.getTitle(),
                report.getReportType(),
                report.getCreatedAt()
        );
    }
}