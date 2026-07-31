package com.insighton.ai.enginealert.repository;

import com.insighton.ai.enginealert.domain.EngineAlert;
import com.insighton.ai.enginealert.domain.Severity;
import java.util.List;

public interface EngineAlertQueryRepository {
    List<EngineAlert> search(Long groupId, Long locationId, Severity severity);
}
