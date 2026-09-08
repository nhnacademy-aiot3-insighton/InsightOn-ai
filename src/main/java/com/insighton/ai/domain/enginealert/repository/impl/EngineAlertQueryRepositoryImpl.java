package com.insighton.ai.domain.enginealert.repository.impl;

import static com.insighton.ai.domain.enginealert.entity.QEngineAlert.engineAlert;

import com.insighton.ai.domain.enginealert.entity.EngineAlert;
import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.repository.EngineAlertQueryRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EngineAlertQueryRepositoryImpl implements EngineAlertQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<EngineAlert> search(Long groupId, Long locationId, Severity severity, OffsetDateTime from,
                                    OffsetDateTime to, int offset, int limit) {
        return queryFactory
                .selectFrom(engineAlert)
                .where(
                        engineAlert.groupId.eq(groupId),
                        locationIdEq(locationId),
                        severityEq(severity),
                        createdAtGoe(from),
                        createdAtLoe(to)
                )
                .orderBy(engineAlert.createdAt.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    @Override
    public long count(Long groupId, Long locationId, Severity severity, OffsetDateTime from, OffsetDateTime to) {
        Long total = queryFactory
                .select(engineAlert.count())
                .from(engineAlert)
                .where(
                        engineAlert.groupId.eq(groupId),
                        locationIdEq(locationId),
                        severityEq(severity),
                        createdAtGoe(from),
                        createdAtLoe(to)
                )
                .fetchOne();
        return total != null ? total : 0L;
    }

    private BooleanExpression locationIdEq(Long locationId) {
        return locationId != null ? engineAlert.locationId.eq(locationId) : null;
    }

    private BooleanExpression severityEq(Severity severity) {
        return severity != null ? engineAlert.severity.eq(severity) : null;
    }

    private BooleanExpression createdAtGoe(OffsetDateTime from) {
        return from != null ? engineAlert.createdAt.goe(from) : null;
    }

    private BooleanExpression createdAtLoe(OffsetDateTime to) {
        return to != null ? engineAlert.createdAt.loe(to) : null;
    }

    @Override
    public List<EngineAlert> searchByPeriod(Long locationId, OffsetDateTime from, OffsetDateTime to) {
        return queryFactory
                .selectFrom(engineAlert)
                .where(
                        engineAlert.locationId.eq(locationId),
                        engineAlert.createdAt.goe(from),
                        engineAlert.createdAt.loe(to)
                )
                .orderBy(engineAlert.createdAt.desc())
                .fetch();
    }
}
