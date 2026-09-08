package com.insighton.ai.domain.enginealert.service;

import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.event.EngineAlertActionEvent;
import java.time.OffsetDateTime;
import java.util.List;

public interface EngineAlertService {

    List<EngineAlertResponse> getEngineAlerts(Long groupId, Long locationId, Severity severity, OffsetDateTime from,
                                              OffsetDateTime to, int offset, int limit);

    long countEngineAlerts(Long groupId, Long locationId, Severity severity, OffsetDateTime from, OffsetDateTime to);

    EngineAlertResponse getEngineAlert(Long engineAlertId, Long userId);

    void createEngineAlert(EngineAlertActionEvent event);

    void deleteByGroup(Long groupId);

    void deleteByLocation(Long locationId);

    EngineAlertSummary summarizePeriod(Long locationId, OffsetDateTime from, OffsetDateTime to);
}
