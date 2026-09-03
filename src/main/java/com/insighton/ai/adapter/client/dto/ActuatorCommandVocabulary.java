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

    /**
     * 지표별 쾌적 기준값(최소~최대). SuggestionGenerationScheduler와 ScheduledActuatorTaskExecutionScheduler가
     * 공통으로 참조 - ACTUATOR_COMMANDS와 같은 이유(각자 선언 시 드리프트 위험)로 여기 둔다.
     */
    public static final Map<String, double[]> COMFORT_RANGE = Map.of(
            "temperature", new double[]{20.0, 26.0},
            "co2", new double[]{0.0, 1000.0},
            "humidity", new double[]{40.0, 60.0}
    );

    /**
     * 예방적 자동화(flow) 생성 대상을 이 시간대 피크로 제한한다. 업무시간 밖(예: 새벽) 피크는 사람이 없어
     * 자동화해도 의미가 없다. ReportGenerationScheduler(리포트 기반)와 FlowRecommendationChatTool(챗봇 기반)
     * 둘 다 참조 - 각자 선언하면 드리프트 위험이 있어 여기 둔다.
     */
    public static final int BUSINESS_HOUR_START = 9;
    public static final int BUSINESS_HOUR_END = 17;

    private ActuatorCommandVocabulary() {
    }
}
