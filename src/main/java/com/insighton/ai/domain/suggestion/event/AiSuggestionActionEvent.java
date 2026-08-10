package com.insighton.ai.domain.suggestion.event;

import java.time.OffsetDateTime;

/**
 * Rule Engine의 AI_SUGGESTION_ACTION 노드가 발행하는 이벤트 페이로드. 정각 스케줄과 무관하게 즉시 제안 생성을 트리거 — SCHEDULE_TRIGGER처럼 센서 값과 무관한 트리거에서
 * 온 경우 deviceId/metricKey/value는 null일 수 있음.
 */
public record AiSuggestionActionEvent(
        Long groupId,
        Long locationId,
        Long deviceId,
        String metricKey,
        Double value,
        OffsetDateTime timestamp
) {
}