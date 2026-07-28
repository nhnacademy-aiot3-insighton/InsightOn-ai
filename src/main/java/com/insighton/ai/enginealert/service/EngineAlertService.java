package com.insighton.ai.enginealert.service;

import com.insighton.ai.enginealert.dto.EngineAlertCreateRequest;
import com.insighton.ai.enginealert.dto.EngineAlertResponse;
import java.util.List;

public interface EngineAlertService {

    List<EngineAlertResponse> findEngineAlerts(Long groupId, Long locationId);

    EngineAlertResponse findEngineAlert(Long engineAlertId, Long userId);

    EngineAlertResponse create(EngineAlertCreateRequest request);

    void deleteByGroup(Long groupId);

    void deleteByLocation(Long locationId);
}
