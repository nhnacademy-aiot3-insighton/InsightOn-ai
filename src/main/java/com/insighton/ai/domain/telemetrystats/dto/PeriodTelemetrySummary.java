package com.insighton.ai.domain.telemetrystats.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PeriodTelemetrySummary(
        Long locationId,
        OffsetDateTime from,
        OffsetDateTime to,
        Map<String, Double> metricsAvg,        // {"temperature":24.3,"co2":820.0,"humidity":52.0}
        Map<String, Double> metricsMax,        // {"temperature":27.8,"co2":1150.0,"humidity":68.0}
        Map<String, Double> metricsMin,        // {"temperature":19.5,"co2":650.0,"humidity":38.0}
        Map<String, Double> actuatorOnMinutes, // {"AIRCON":1930.0,"AIR_PURIFIER":640.0}
        // 지표별 시간대(0~23시)별 평균 — 월간 리포트에서 "몇 시에 어떤 지표가 오르는지" 패턴을 뽑아내는 데 사용
        // ex) {"co2": {0: 620.0, 1: 600.0, ..., 14: 1050.0, ...}}
        Map<String, Map<Integer, Double>> hourlyAvgByMetric
) {
}