package com.insighton.ai.enginealert.dto;

import com.insighton.ai.enginealert.domain.Severity;
import java.math.BigDecimal;

/**
 * Rule Engine의 ALERT_ACTION 노드가 발행하는 이벤트 페이로드. LLM 개입 없이 즉시 뜨는 알림을 생성하는 유일한 경로 —
 * title/message/severity는 flow 작성자가 노드 설정 시점에 직접 입력한 값이며 AI가 채우지 않는다.
 */
public record AiAlertActionEvent(
        Long groupId,
        Long locationId,
        Long flowId,
        String title,
        String message,
        Severity severity,
        BigDecimal triggerValue
) {
}
