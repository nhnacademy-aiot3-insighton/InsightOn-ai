package com.insighton.ai.coreapi.domain;

import java.util.Set;

/**
 * 액추에이터 명령별 허용 값 검증 규칙. Core의 CommandValueRule(ACTUATOR_PRESET)과 동일한 구조로 AI 쪽에도 미러링해서, LLM이 생성한 commandValue를 저장/실행 전에
 * 한 번 더 검증하는 데 씀.
 */
public sealed interface CommandValueRule {

    boolean isValid(String value);

    record AllowedValues(Set<String> values) implements CommandValueRule {
        @Override
        public boolean isValid(String value) {
            return value != null && values.contains(value.toUpperCase());
        }
    }

    record NumericRange(double min, double max) implements CommandValueRule {
        @Override
        public boolean isValid(String value) {
            try {
                double parsed = Double.parseDouble(value);
                return parsed >= min && parsed <= max;
            } catch (NumberFormatException | NullPointerException e) {
                return false;
            }
        }
    }
}
