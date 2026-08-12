package com.insighton.ai.controller.api;

import com.insighton.ai.controller.swagger.ReportApi;
import com.insighton.ai.domain.report.dto.ReportDetailResponse;
import com.insighton.ai.domain.report.dto.ReportListResponse;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
    public ResponseEntity<Page<ReportListResponse>> getReports(
            @RequestParam Long groupId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        List<ReportListResponse> content = reportService.findReports(groupId, locationId, reportType, from, to,
                (int) pageable.getOffset(), pageable.getPageSize());
        long total = reportService.countReports(groupId, locationId, reportType, from, to);

        return ResponseEntity.ok(new PageImpl<>(content, pageable, total));
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
