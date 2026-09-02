package com.insighton.ai.adapter.client.dto;

import java.util.Map;

/**
 * 액추에이터 종류별로 허용되는 명령·값 목록. LLM 프롬프트에 "이 조합만 쓰라"고 보여줄 때 쓴다(SuggestionGenerationScheduler,
 * ReportGenerationScheduler 둘 다 이 상수를 참조 - 예전엔 각자 따로 선언해서 드리프트 위험이 있었음).
 * Core com.insighton.core.domain.actuators.policy의 CommandType/CommandValueRule 확정값과 동일하게 유지할 것.
 */
public final class ActuatorCommandVocabulary {

    public static final Map<String, Map<String, String>> ACTUATOR_COMMANDS = Map.of(
            "AIRCON", Map.of(
                    "POWER_STATUS", "ON, OFF",
                    "OPERATION_MODE", "COOL, DRY, FAN, AUTO",
                    "SET_TEMPERATURE", "18~30 사이 숫자"
            ),
            "AIR_PURIFIER", Map.of(
                    "POWER_STATUS", "ON, OFF",
                    "OPERATION_MODE", "AUTO, SLEEP, TURBO"
            ),
            "VENTILATION_FAN", Map.of(
                    "POWER_STATUS", "ON, OFF",
                    "OPERATION_MODE", "LOW, MID, HIGH"
            )
    );

    private ActuatorCommandVocabulary() {
    }
}
