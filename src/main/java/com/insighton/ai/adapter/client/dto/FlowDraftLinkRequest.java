package com.insighton.ai.adapter.client.dto;

/**
 * Rule Engine flow 초안 생성 요청의 링크 하나. AI가 만드는 flow는 항상 SCHEDULE(포트 "out") → ACTUATOR_CONTROL(포트 "in")
 * 2노드 1링크 고정 템플릿이라 포트 이름도 이 값으로 고정된다.
 */
public record FlowDraftLinkRequest(
        String sourceClientNodeKey,
        String targetClientNodeKey,
        String sourcePort,
        String targetPort
) {
}
