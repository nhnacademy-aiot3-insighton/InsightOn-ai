package com.insighton.ai.report.controller;

import com.insighton.ai.report.domain.ReportType;
import com.insighton.ai.report.dto.ReportDetailResponse;
import com.insighton.ai.report.dto.ReportListResponse;
import com.insighton.ai.report.service.ReportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController implements ReportApi {

    private final ReportService reportService;

    @Override
    @GetMapping
    public ResponseEntity<List<ReportListResponse>> getReports(
            @RequestParam Long groupId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) ReportType reportType
    ) {
        return ResponseEntity.ok(reportService.findReports(groupId, locationId, reportType));
    }

    @Override
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportDetailResponse> getReport(
            @PathVariable Long reportId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(reportService.findReport(reportId, userId));
    }
}
