package com.insighton.ai.suggestion.dto;

/**
 * 리포트 생성 배치가 기간 내 제안 현황을 요약할 때 쓰는 집계 결과.
 */
public record SuggestionSummary(
        long totalCount,
        long acceptedCount,
        long rejectedCount,
        long pendingCount
) {
}
