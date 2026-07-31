package com.insighton.ai.enginealert.service;

import com.insighton.ai.enginealert.domain.Severity;
import com.insighton.ai.enginealert.dto.EngineAlertCreateRequest;
import com.insighton.ai.enginealert.dto.EngineAlertResponse;
import java.util.List;

public interface EngineAlertService {

    List<EngineAlertResponse> getEngineAlerts(Long groupId, Long locationId, Severity severity);

    EngineAlertResponse getEngineAlert(Long engineAlertId);

    EngineAlertResponse createEngineAlert(EngineAlertCreateRequest request);

}
