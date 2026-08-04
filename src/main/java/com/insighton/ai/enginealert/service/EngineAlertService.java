package com.insighton.ai.enginealert.service;

import com.insighton.ai.enginealert.domain.Severity;
import com.insighton.ai.enginealert.dto.EngineAlertCreateRequest;
import com.insighton.ai.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.enginealert.dto.EngineAlertSummary;
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
