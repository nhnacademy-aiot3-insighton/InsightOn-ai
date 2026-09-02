package com.insighton.ai.domain.report.dto;

import java.util.Map;

/**
 * 리포트 대상 위치를 같은 그룹 내 다른 위치들과 비교한 결과. metricDiffs/actuatorDiffs엔 그룹 평균 대비 차이가 임계치(±15%) 이상인 항목만 담긴다 —
 * 사소한 차이까지 리포트에 억지로 언급하지 않기 위함.
 */
public record GroupComparisonSummary(
        int comparedLocationCount,
        Map<String, MetricDiff> metricDiffs,
        Map<String, MetricDiff> actuatorDiffs
) {
    public static GroupComparisonSummary empty() {
        return new GroupComparisonSummary(0, Map.of(), Map.of());
    }
}
