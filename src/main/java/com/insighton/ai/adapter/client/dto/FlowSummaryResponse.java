package com.insighton.ai.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Rule Engine의 flow 목록 조회(GET /internal/v1/flows) 응답 - 리포트에 "관리 중인 자동화" 섹션을 만들 때 쓴다.
 * description엔 FlowDraftRequester가 생성 시점에 남긴 판단 근거 문장이 그대로 들어있어, 별도 가공 없이도
 * "왜 이 자동화를 만들었는지"를 바로 알 수 있다. Rule Engine의 실제 FlowResponse엔 groupId/locationId/createdAt 등
 * 더 많은 필드가 있지만 여기선 쓰는 것만 받는다 - ignoreUnknown 없으면 그 필드들 때문에 역직렬화가 깨진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowSummaryResponse(
        Long flowId,
        String name,
        String description,
        String status
) {
}
