package com.insighton.ai.domain.telemetrystats.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PeriodTelemetrySummary(
        Long locationId,
        OffsetDateTime from,
        OffsetDateTime to,
        Map<String, Double> metricsAvg,        // 지표별 평균값
        Map<String, Double> metricsMax,        // 지표별 최고값
        Map<String, Double> metricsMin,        // 지표별 최저값
        Map<String, Double> actuatorOnMinutes, // 액추에이터 타입별 가동 시간(분)
        // 지표별 시간대(0~23시)별 평균 — 월간 리포트에서 "몇 시에 어떤 지표가 오르는지" 패턴을 뽑아내는 데 사용
        Map<String, Map<Integer, Double>> hourlyAvgByMetric
) {
}