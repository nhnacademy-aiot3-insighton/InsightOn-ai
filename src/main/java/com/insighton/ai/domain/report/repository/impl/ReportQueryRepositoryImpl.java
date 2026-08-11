package com.insighton.ai.domain.report.repository.impl;

import static com.insighton.ai.domain.report.entity.QReport.report;

import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.repository.ReportQueryRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReportQueryRepositoryImpl implements ReportQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Report> search(Long groupId, Long locationId, ReportType reportType,
                               OffsetDateTime from, OffsetDateTime to) {
        return queryFactory
                .selectFrom(report)
                .where(
                        report.groupId.eq(groupId),
                        locationIdEq(locationId),
                        reportTypeEq(reportType),
                        createdAtGoe(from),
                        createdAtLoe(to)
                )
                .orderBy(report.createdAt.desc())
                .fetch();
    }

    private BooleanExpression locationIdEq(Long locationId) {
        return locationId != null ? report.locationId.eq(locationId) : null;
    }

    private BooleanExpression reportTypeEq(ReportType reportType) {
        return reportType != null ? report.reportType.eq(reportType) : null;
    }

    private BooleanExpression createdAtGoe(OffsetDateTime from) {
        return from != null ? report.createdAt.goe(from) : null;
    }

    private BooleanExpression createdAtLoe(OffsetDateTime to) {
        return to != null ? report.createdAt.loe(to) : null;
    }
}
