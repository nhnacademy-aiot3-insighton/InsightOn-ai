package com.insighton.ai.report.controller;

import com.insighton.ai.exception.ErrorResponse;
import com.insighton.ai.report.domain.ReportType;
import com.insighton.ai.report.dto.ReportDetailResponse;
import com.insighton.ai.report.dto.ReportListResponse;
import com.insighton.ai.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports", description = "주간/월간 리포트 조회 API")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(
            summary = "리포트 목록 조회",
            description = "groupId 기준으로 리포트 목록을 조회합니다. locationId/reportType으로 추가 필터링 가능하며 본문(content)은 포함하지 않습니다."
    )

    @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportListResponse.class))))
    @GetMapping
    public ResponseEntity<List<ReportListResponse>> getReports(
            @Parameter(description = "그룹 ID", example = "5", required = true, schema = @Schema(type = "integer", format = "int64"))
            @RequestParam Long groupId,
            @Parameter(description = "위치 ID", example = "42", schema = @Schema(type = "integer", format = "int64"))
            @RequestParam(required = false) Long locationId,
            @Parameter(description = "리포트 종류", example = "WEEKLY")
            @RequestParam(required = false) ReportType reportType
    ) {
        return ResponseEntity.ok(reportService.findReports(groupId, locationId, reportType));
    }

    @Operation(summary = "리포트 상세 조회", description = "리포트 ID로 본문을 포함한 상세 내용을 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = ReportDetailResponse.class)))
    @ApiResponse(responseCode = "404", description = "리포트 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{report-id}")
    public ResponseEntity<ReportDetailResponse> getReport(
            @Parameter(description = "리포트 ID", example = "1",
                    schema = @Schema(type = "integer", format = "int64"))
            @PathVariable("report-id") Long reportId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(reportService.findReport(reportId, userId));
    }
}
