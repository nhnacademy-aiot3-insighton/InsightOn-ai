package com.insighton.ai.enginealert.repository.impl;

import static com.insighton.ai.enginealert.domain.QEngineAlert.engineAlert;

import com.insighton.ai.enginealert.domain.EngineAlert;
import com.insighton.ai.enginealert.repository.EngineAlertQueryRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EngineAlertQueryRepositoryImpl implements EngineAlertQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<EngineAlert> search(Long groupId, Long locationId) {
        return queryFactory
                .selectFrom(engineAlert)
                .where(
                        engineAlert.groupId.eq(groupId),
                        locationIdEq(locationId)
                )
                .orderBy(engineAlert.createdAt.desc())
                .fetch();
    }

    private BooleanExpression locationIdEq(Long locationId) {
        return locationId != null ? engineAlert.locationId.eq(locationId) : null;
    }
}
