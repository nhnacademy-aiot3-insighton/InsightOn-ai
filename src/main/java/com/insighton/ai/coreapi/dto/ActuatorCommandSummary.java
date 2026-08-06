package com.insighton.ai.coreapi.dto;

import java.util.Map;

public record ActuatorCommandSummary(
        double avgSetTemperature,           // SET_TEMPERATURE 명령들의 평균값
        int setTemperatureChangeCount,      // 총 변경 횟수
        Map<String, Long> executedByRatio   // {"USER":30, "RULE_ENGINE":65, "AI_SYSTEM":5} (%)
) {
}