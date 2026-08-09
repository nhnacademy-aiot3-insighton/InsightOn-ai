package com.insighton.ai.domain.report.service;

import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.dto.ReportCreateRequest;
import com.insighton.ai.domain.report.dto.ReportDetailResponse;
import com.insighton.ai.domain.report.dto.ReportListResponse;
import java.util.List;

public interface ReportService {
    List<ReportListResponse> findReports(Long groupId, Long locationId, ReportType reportType);

    ReportDetailResponse findReport(Long reportId, Long userId);

    Report createReport(ReportCreateRequest request);

    void deleteByGroup(Long groupId);

    void deleteByLocation(Long locationId);
}
