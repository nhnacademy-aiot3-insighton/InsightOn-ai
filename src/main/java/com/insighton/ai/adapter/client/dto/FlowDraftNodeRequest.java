package com.insighton.ai.adapter.client.dto;

import java.util.Map;

/**
 * Rule Engine flow 초안 생성 요청(POST /internal/v1/flows)의 노드 하나. nodeType은 Rule Engine의 실제 NodeType enum 값 문자열
 * ("SCHEDULE"/"ACTUATOR_CONTROL")을 그대로 쓴다.
 */
public record FlowDraftNodeRequest(
        String clientNodeKey,
        String nodeType,
        Map<String, Object> configuration
) {
}
