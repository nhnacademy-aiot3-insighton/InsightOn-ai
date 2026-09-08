package com.insighton.ai.domain.report.dto;

/**
 * 리포트의 "그룹 내 비교" 섹션에서 쓰는 단일 지표/액추에이터 항목의 차이. thisValue는 이 위치 값, groupAvg는 같은 그룹 내 다른 위치들의 평균, percentDiff는
 * groupAvg 대비 백분율 차이(양수=그룹 평균보다 높음).
 */
public record MetricDiff(double thisValue, double groupAvg, double percentDiff) {
}
