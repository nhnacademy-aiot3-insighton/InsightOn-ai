package com.insighton.ai.domain.report.dto;

import com.insighton.ai.adapter.client.dto.ActuatorType;

/**
 * LLM이 시간대별 패턴({@link com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern}) 하나에 대해, 예방적 자동화 flow를
 * 만들 만한지와 만든다면 어떤 명령을 쓸지 직접 판단한 결과(구조화 출력). automationRecommended=false면 이 패턴은 flow 초안 생성 대상에서 제외된다.
 */
public record FlowActionDecision(
        String metric,
        boolean automationRecommended,
        ActuatorType actuatorType,
        String command,
        String commandValue
) {
}
