package com.insighton.ai.enginealert.service;

import com.insighton.ai.enginealert.dto.EngineAlertCreateRequest;
import com.insighton.ai.enginealert.dto.EngineAlertResponse;
import java.util.List;

public interface EngineAlertService {

    List<EngineAlertResponse> getEngineAlerts(Long groupId, Long locationId);

    EngineAlertResponse getEngineAlert(Long engineAlertId);

    EngineAlertResponse createEngineAlert(EngineAlertCreateRequest request);

}
