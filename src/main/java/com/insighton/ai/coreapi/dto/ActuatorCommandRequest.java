package com.insighton.ai.coreapi.dto;

import com.insighton.ai.coreapi.domain.ExecutedByType;

/**
 * Core 액추에이터 제어 API(PUT /internal/locations/{location-id}/actuators/state) 요청 바디. locationId는 path에 있어서 body엔 포함하지 않고,
 * callerService(AI 쪽에선 항상 ExecutedByType.AI_SYSTEM)는 Core의 actuator_run_logs.executed_by_type으로 그대로 저장되는 데이터라 헤더가 아니라
 * body 필드로 담음
 */
public record ActuatorCommandRequest(
        String actuatorType,
        String command,
        String commandValue,
        ExecutedByType callerService
) {
}
