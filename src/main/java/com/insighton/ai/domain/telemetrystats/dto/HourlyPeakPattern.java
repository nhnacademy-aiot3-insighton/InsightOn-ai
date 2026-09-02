package com.insighton.ai.domain.telemetrystats.dto;

/**
 * 기간 내 특정 지표가 그 기간 평균(baselineAvg) 대비 통계적으로 유의미하게 튀는 시간대. 월간 리포트의 "시간대별 패턴" 서술에 쓰이고, 추후 이 시간대를 근거로 한 Rule
 * Engine 자동화 flow 제안(scheduleCron)의 입력으로도 재사용될 예정이다.
 */
public record HourlyPeakPattern(
        String metric,
        int peakHour,
        double peakValue,
        double baselineAvg,
        double percentAboveBaseline
) {
}
