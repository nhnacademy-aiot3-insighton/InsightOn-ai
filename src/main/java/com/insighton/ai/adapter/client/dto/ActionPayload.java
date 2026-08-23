package com.insighton.ai.adapter.client.dto;

/**
 * AI가 판단한 액추에이터 조작 내용. SuggestionLog에 JSON으로 저장해두고 나중에(제안 수락 시,
 * 또는 AI_DIRECT 자동 실행 시) 다시 읽어서 씀. Core 호출 시점엔 이 그대로 보내지 않고
 * {@link ActuatorCommandRequest}로 변환해서 보낸다(Core가 요구하는 callerService가 여기 없음).
 */
public record ActionPayload(
        Long locationId,
        String actuatorType,
        String command,
        String commandValue
) {
}
