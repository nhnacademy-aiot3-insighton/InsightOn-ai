package com.insighton.ai.domain.report.dto;

import java.util.List;

/**
 * buildFlowActionPrompt() 호출 하나로 시간대별 패턴 전체에 대한 판단을 한 번에 받기 위한 LLM 구조화 출력 래퍼.
 */
public record FlowActionDecisions(
        List<FlowActionDecision> decisions
) {
}
