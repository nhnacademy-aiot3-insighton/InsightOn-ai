package com.insighton.ai.adapter.client.dto;

import java.util.Map;

/**
 * Core의 내부 액추에이터 제어 API(PUT /internal/v1/locations/{location-id}/actuators/state)
 * 요청 바디. locationId는 경로 변수로 따로 넘기므로 여기 포함하지 않는다.
 * callerService는 Core의 ExecutedByType과 이름이 같아야 한다 - AI는 항상 "AI_SYSTEM"을 보낸다
 * (USER는 이 내부 API에서 거부됨).
 * command는 Core의 CommandType.stateKey(짧은 문자열, 예: "power")와 매칭돼야 한다 —
 * AI 내부(LLM 프롬프트/ActionPayload)는 POWER_STATUS/OPERATION_MODE/SET_TEMPERATURE라는
 * enum 이름을 그대로 쓰므로, {@link #of}가 Core로 나가는 시점에 stateKey로 변환한다.
 */
public record ActuatorCommandRequest(
        String actuatorType,
        String command,
        String commandValue,
        CallerService callerService
) {
    // Rule Engine의 ACTUATOR_CONTROL 노드도 이 짧은 키(power/mode/temperature)를 기대하므로
    // FlowDraftRequester가 그대로 재사용할 수 있게 public으로 둔다.
    public static final Map<String, String> COMMAND_TO_CORE_STATE_KEY = Map.of(
            "POWER_STATUS", "power",
            "OPERATION_MODE", "mode",
            "SET_TEMPERATURE", "temperature"
    );

    public static ActuatorCommandRequest of(String actuatorType, String command, String commandValue,
                                            CallerService callerService) {
        // LLM(제안/챗봇)이 만든 명령이 실제로 허용된 조합인지 Core로 나가기 직전 마지막으로 검증한다 -
        // 프롬프트 지시만으로는 SET_TEMPERATURE=999 같은 값도 그대로 통과해버렸다.
        ActuatorCommandVocabulary.validate(actuatorType, command, commandValue);
        return new ActuatorCommandRequest(actuatorType,
                COMMAND_TO_CORE_STATE_KEY.getOrDefault(command, command), commandValue, callerService);
    }
}
