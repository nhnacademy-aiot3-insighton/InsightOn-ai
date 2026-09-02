package com.insighton.ai.domain.suggestion.dto;

/**
 * location별 최근 제안 중 (actuatorType, command, commandValue) 조합 단위로 집계한 거절 패턴. SET_TEMPERATURE처럼 값이 연속적인 command는 그룹핑 의미가
 * 없어 집계 대상에서 제외
 */
public record RejectionPattern(
        String actuatorType,
        String command,
        String commandValue,
        long rejectedCount,
        long totalCount
) {
}