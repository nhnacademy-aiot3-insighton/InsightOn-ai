package com.insighton.ai.domain.report.exception;

public class ReportNotFoundException extends RuntimeException {

    private final Long reportId;

    public ReportNotFoundException(Long reportId) {
        super("Report not found: " + reportId);
        this.reportId = reportId;
    }

    public Long getReportId() {
        return reportId;
    }
}
