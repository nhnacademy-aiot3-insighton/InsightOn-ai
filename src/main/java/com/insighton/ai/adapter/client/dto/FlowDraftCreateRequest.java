package com.insighton.ai.adapter.client.dto;

import java.util.List;

/**
 * Rule Engine 내부 API(POST /internal/v1/flows) 요청 바디. groupId는 별도 쿼리 파라미터로 보내고(RuleEngineClient 참고) 여기 담지 않는다.
 * 항상 SCHEDULE → ACTUATOR_CONTROL 2노드 1링크 고정 템플릿만 보낸다(자세한 계약은 rule-engine-flow-draft-request 참고). 생성되는 flow는 Rule
 * Engine 쪽에서 항상 비활성 초안(INACTIVE)으로 만들어지므로 이 요청엔 활성화 여부를 담지 않는다.
 *
 * <p>Rule Engine의 flows 테이블엔 생성 주체·출처를 담을 별도 컬럼이 없어서(스키마 변경 불가), 그 정보는 필드로 안 보내고
 * description 문장 안에 사람이 읽는 텍스트로 녹여 넣는다(FlowDraftRequester.buildDescription() 참고).
 */
public record FlowDraftCreateRequest(
        Long locationId,
        String name,
        String description,
        List<FlowDraftNodeRequest> nodes,
        List<FlowDraftLinkRequest> links
) {
}
