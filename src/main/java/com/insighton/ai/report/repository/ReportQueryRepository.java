package com.insighton.ai.report.repository;

import com.insighton.ai.report.domain.Report;
import com.insighton.ai.report.domain.ReportType;
import java.util.List;

public interface ReportQueryRepository {
    List<Report> search(Long groupId, Long locationId, ReportType reportType);
}
