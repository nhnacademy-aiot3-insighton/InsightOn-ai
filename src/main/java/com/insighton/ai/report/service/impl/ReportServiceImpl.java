package com.insighton.ai.report.service.impl;

import com.insighton.ai.report.dto.ReportCreateRequest;
import com.insighton.ai.report.dto.ReportDetailResponse;
import com.insighton.ai.report.dto.ReportListResponse;
import com.insighton.ai.report.entity.Report;
import com.insighton.ai.report.entity.ReportType;
import com.insighton.ai.report.exception.ReportNotFoundException;
import com.insighton.ai.report.repository.ReportRepository;
import com.insighton.ai.report.service.ReportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    @Override
    public List<ReportListResponse> findReports(Long groupId, Long locationId, ReportType reportType) {

        return reportRepository.search(groupId, locationId, reportType).stream()
                .map(ReportListResponse::from)
                .toList();
    }

    @Override
    public ReportDetailResponse findReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));

        log.info("리포트 조회 - reportId={}", reportId);
        return ReportDetailResponse.from(report);
    }

    @Transactional
    @Override
    public Report createReport(ReportCreateRequest request) {
        Report report = Report.builder()
                .groupId(request.groupId())
                .locationId(request.locationId())
                .reportType(request.reportType())
                .content(request.content())
                .build();

        Report savedReport = reportRepository.save(report);
        log.info("리포트 생성 - reportId={}", savedReport.getReportId());
        return savedReport;
    }
}
