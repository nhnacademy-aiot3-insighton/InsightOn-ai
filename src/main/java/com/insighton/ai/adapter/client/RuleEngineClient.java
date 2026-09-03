package com.insighton.ai.adapter.client;

import com.insighton.ai.adapter.client.dto.FlowDraftCreateRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftResponse;
import com.insighton.ai.adapter.client.dto.FlowSummaryResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * 리포트의 "관리 중인 자동화" 섹션용 - 위치의 flow 목록을 조회한다(신규 요청, rule-engine-flow-duplicate-check-request 참고).
     * AI가 만든 것과 유저가 직접 만든 것 구분 없이 다 내려오므로, "[AI] " 접두어로 시작하는 것만 AI 쪽에서 걸러 쓴다.
     */
    @GetMapping("/flows")
    List<FlowSummaryResponse> findFlows(@RequestParam("groupId") Long groupId,
                                        @RequestParam("locationId") Long locationId);
}
