package com.insighton.ai.domain.report.repository;

import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import java.time.OffsetDateTime;
import java.util.List;

public interface ReportQueryRepository {
    List<Report> search(Long groupId, Long locationId, ReportType reportType,
                        OffsetDateTime from, OffsetDateTime to, int offset, int limit);

    long count(Long groupId, Long locationId, ReportType reportType,
              OffsetDateTime from, OffsetDateTime to);
}
