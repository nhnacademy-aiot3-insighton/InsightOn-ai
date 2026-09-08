package com.insighton.ai.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Rule Engine의 실제 FlowResponse엔 groupId/locationId/name/description/createdAt도 있지만 여기선 쓰는 것만 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowDraftResponse(
        Long flowId,
        String status,
        Long replacedFlowId
) {
}
