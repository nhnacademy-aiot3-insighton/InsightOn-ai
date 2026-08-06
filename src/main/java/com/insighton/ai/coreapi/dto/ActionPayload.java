package com.insighton.ai.coreapi.dto;

/**
 * AI가 판단한 액추에이터 조작 내용을 Core 제어 API(POST /internal/actuators/commands)에 전달할 때 쓰는 요청 바디.
 */
public record ActionPayload(
        Long locationId,
        String actuatorType,
        String command,
        String commandValue
) {
}
