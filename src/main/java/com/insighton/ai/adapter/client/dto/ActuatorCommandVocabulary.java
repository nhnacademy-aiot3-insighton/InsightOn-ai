package com.insighton.ai.adapter.client.dto;

import com.insighton.ai.adapter.client.exception.InvalidActuatorCommandException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
     * {@link #ACTUATOR_COMMANDS}는 LLM 프롬프트용 자유 텍스트라 코드로 검증할 수 없다. LLM(제안/자동화/챗봇)이
     * 만들어낸 명령이 실제로 이 목록 안에 있는지는 프롬프트 지시에만 의존하고 있었고, 코드 레벨 검증이
     * 없었다 - {@link #validate}가 그 자리를 채운다. 위 프롬프트용 텍스트와 값이 어긋나지 않게 손볼 것.
     */
    private static final Map<String, Map<String, CommandValueRule>> ACTUATOR_COMMAND_RULES = Map.of(
            "AIRCON", Map.of(
                    "POWER_STATUS", new CommandValueRule.AllowedValues(Set.of("ON", "OFF")),
                    "OPERATION_MODE", new CommandValueRule.AllowedValues(Set.of("COOL", "DRY", "FAN", "AUTO")),
                    "SET_TEMPERATURE", new CommandValueRule.NumericRange(18, 30)
            ),
            "AIR_PURIFIER", Map.of(
                    "POWER_STATUS", new CommandValueRule.AllowedValues(Set.of("ON", "OFF")),
                    "OPERATION_MODE", new CommandValueRule.AllowedValues(Set.of("AUTO", "SLEEP", "TURBO"))
            ),
            "VENTILATION_FAN", Map.of(
                    "POWER_STATUS", new CommandValueRule.AllowedValues(Set.of("ON", "OFF")),
                    "OPERATION_MODE", new CommandValueRule.AllowedValues(Set.of("LOW", "MID", "HIGH"))
            )
    );

    /**
     * LLM이 만든 명령을 실제로 실행하기 전(Core 호출 직전) 마지막 관문. 존재하지 않는 액추에이터 타입,
     * 정의 안 된 명령, 허용 범위 밖의 값(예: SET_TEMPERATURE=999) 전부 여기서 걸러진다.
     */
    public static void validate(String actuatorType, String command, String commandValue) {
        Map<String, CommandValueRule> commandRules = ACTUATOR_COMMAND_RULES.get(actuatorType);
        CommandValueRule rule = commandRules != null ? commandRules.get(command) : null;
        if (rule == null || !rule.isValid(commandValue)) {
            throw new InvalidActuatorCommandException(actuatorType, command, commandValue);
        }
    }

    private sealed interface CommandValueRule {

        boolean isValid(String value);

        record AllowedValues(Set<String> values) implements CommandValueRule {
            @Override
            public boolean isValid(String value) {
                return value != null && values.contains(value.toUpperCase(Locale.ROOT));
            }
        }

        record NumericRange(double min, double max) implements CommandValueRule {
            @Override
            public boolean isValid(String value) {
                try {
                    double parsed = Double.parseDouble(value);
                    return parsed >= min && parsed <= max;
                } catch (NullPointerException | NumberFormatException e) {
                    return false;
                }
            }
        }
    }

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
