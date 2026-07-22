package com.insighton.ai.report.service;

import com.insighton.ai.report.dto.ReportCreateRequest;
import com.insighton.ai.report.dto.ReportDetailResponse;
import com.insighton.ai.report.dto.ReportListResponse;
import com.insighton.ai.report.entity.Report;
import com.insighton.ai.report.entity.ReportType;
import java.util.List;

public interface ReportService {
    List<ReportListResponse> findReports(Long groupId, Long locationId, ReportType reportType);

    ReportDetailResponse findReport(Long reportId);

    Report createReport(ReportCreateRequest request);
}
