package com.insighton.ai.report.repository;

import com.insighton.ai.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long>, ReportQueryRepository {
}
