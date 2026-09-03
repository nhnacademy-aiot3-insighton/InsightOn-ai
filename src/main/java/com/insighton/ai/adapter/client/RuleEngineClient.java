package com.insighton.ai.adapter.client;

import com.insighton.ai.adapter.client.dto.FlowDraftCreateRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-ruleengine", path = "/internal/v1", url = "${rule-engine-service.url}")
public interface RuleEngineClient {

    /**
     * AI가 리포트에서 찾은 시간대 패턴을 근거로 Rule Engine에 (비활성 초안 상태의) 자동화 flow 생성을 요청한다. 같은 (groupId, locationId, name) 조합으로 이미 만들어진
     * flow가 있으면 Rule Engine이 새로 만들지 않고 기존 것을 그대로 반환한다(flows 테이블의 기존 유니크 제약을 재사용한 멱등 처리 - 별도 idempotency key가 필요 없다).
     */
    @PostMapping("/flows")
    FlowDraftResponse createAiDraft(@RequestParam("groupId") Long groupId,
                                    @RequestBody FlowDraftCreateRequest request);
}
