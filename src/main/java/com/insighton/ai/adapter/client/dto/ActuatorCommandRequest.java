package com.insighton.ai.adapter.client.dto;

/**
 * Core의 내부 액추에이터 제어 API(PUT /internal/v1/locations/{location-id}/actuators/state)
 * 요청 바디. locationId는 경로 변수로 따로 넘기므로 여기 포함하지 않는다.
 * callerService는 Core의 ExecutedByType과 이름이 같아야 한다 - AI는 항상 "AI_SYSTEM"을 보낸다
 * (USER는 이 내부 API에서 거부됨).
 */
public record ActuatorCommandRequest(
        String actuatorType,
        String command,
        String commandValue,
        CallerService callerService
) {
}
