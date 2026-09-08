package com.insighton.ai.domain.enginealert.repository;

import com.insighton.ai.domain.enginealert.entity.EngineAlert;
import com.insighton.ai.domain.enginealert.entity.Severity;
import java.time.OffsetDateTime;
import java.util.List;

public interface EngineAlertQueryRepository {
    List<EngineAlert> search(Long groupId, Long locationId, Severity severity, OffsetDateTime from,
                             OffsetDateTime to, int offset, int limit);

    long count(Long groupId, Long locationId, Severity severity, OffsetDateTime from, OffsetDateTime to);

    List<EngineAlert> searchByPeriod(Long locationId, OffsetDateTime from, OffsetDateTime to);
}
