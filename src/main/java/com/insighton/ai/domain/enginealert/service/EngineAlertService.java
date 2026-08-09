package com.insighton.ai.domain.enginealert.service;

import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.dto.EngineAlertCreateRequest;
import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import java.time.OffsetDateTime;
import java.util.List;

public interface EngineAlertService {

    List<EngineAlertResponse> getEngineAlerts(Long groupId, Long locationId, Severity severity);

    EngineAlertResponse getEngineAlert(Long engineAlertId, Long userId);

    EngineAlertResponse createEngineAlert(EngineAlertCreateRequest request);

    void deleteByGroup(Long groupId);

    void deleteByLocation(Long locationId);

    EngineAlertSummary summarizePeriod(Long locationId, OffsetDateTime from, OffsetDateTime to);
}
