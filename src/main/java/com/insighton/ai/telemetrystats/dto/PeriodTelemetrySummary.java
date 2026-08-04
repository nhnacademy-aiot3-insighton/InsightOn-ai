package com.insighton.ai.telemetrystats.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PeriodTelemetrySummary(
        Long locationId,
        OffsetDateTime from,
        OffsetDateTime to,
        Map<String, Double> metricsAvg,        // {"temperature":24.3,"co2":820.0,"humidity":52.0}
        Map<String, Double> metricsMax,        // {"temperature":27.8,"co2":1150.0,"humidity":68.0}
        Map<String, Double> metricsMin,        // {"temperature":19.5,"co2":650.0,"humidity":38.0}
        Map<String, Double> actuatorOnMinutes  // {"AIRCON":1930.0,"AIR_PURIFIER":640.0}
) {
}