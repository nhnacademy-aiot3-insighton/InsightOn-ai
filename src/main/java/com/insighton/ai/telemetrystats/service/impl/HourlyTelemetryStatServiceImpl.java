package com.insighton.ai.telemetrystats.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insighton.ai.exception.InvalidRequestException;
import com.insighton.ai.telemetrystats.domain.HourlyTelemetryStat;
import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.telemetrystats.repository.HourlyTelemetryStatRepository;
import com.insighton.ai.telemetrystats.service.HourlyTelemetryStatService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시간별 텔레메트리 통계 조회·생성 담당 서비스 구현체.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HourlyTelemetryStatServiceImpl implements HourlyTelemetryStatService {

    private final HourlyTelemetryStatRepository hourlyTelemetryStatRepository;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    /**
     * 위치 ID(필수), 기간(선택) 조건에 따른 시간별 통계 목록 조회.
     *
     * @param locationId 위치 ID(필수)
     * @param from       조회 시작 시각(선택)
     * @param to         조회 종료 시각(선택)
     * @return 시간별 통계 목록 응답
     * @throws InvalidRequestException locationId가 null인 경우
     */
    @Override
    public List<HourlyTelemetryStatResponse> findHourlyTelemetryStats(Long locationId,
                                                                      OffsetDateTime from, OffsetDateTime to) {

        if (locationId == null) {
            throw new InvalidRequestException("locationId 는 필수값입니다.");
        }

        List<HourlyTelemetryStat> stats = hourlyTelemetryStatRepository.search(locationId, from, to);

        log.info("시간별 통계 조회 - locationId:{}, from:{}, to:{}, size:{}",
                locationId, from, to, stats.size());
        return stats.stream()
                .map(HourlyTelemetryStatResponse::from)
                .toList();
    }

    /**
     * 시간별 통계 신규 생성, 저장 전 Bean Validation 기반 요청값 유효성 검증 수행.
     *
     * @param request 시간별 통계 생성 요청
     * @return 저장된 시간별 통계 응답
     * @throws ConstraintViolationException 요청값 검증 실패 시
     */
    @Transactional
    @Override
    public HourlyTelemetryStatResponse create(HourlyTelemetryStatCreateRequest request) {

        Set<ConstraintViolation<HourlyTelemetryStatCreateRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        HourlyTelemetryStat stat = HourlyTelemetryStat.builder()
                .locationId(request.locationId())
                .logHour(request.logHour())
                .metricsAvg(request.metricsAvg())
                .metricsMax(request.metricsMax())
                .metricsMin(request.metricsMin())
                .actuatorOnMinutes(request.actuatorOnMinutes())
                .build();

        HourlyTelemetryStat saveStat = hourlyTelemetryStatRepository.save(stat);

        log.info("시간별 통계 저장 - locationId:{}, logHour:{}",
                saveStat.getLocationId(), saveStat.getLogHour());

        return HourlyTelemetryStatResponse.from(saveStat);
    }

    /**
     * 위치 ID·집계 시간대 복합 키 기준 단건 조회 (내부용 — 정각 집계 배치의 중복 집계 방지 체크에 사용).
     *
     * @param locationId 위치 ID(필수)
     * @param logHour    집계 시간대(필수)
     * @return 존재하면 시간별 통계 응답, 없으면(=아직 집계 전) 빈 Optional
     * @throws InvalidRequestException locationId 또는 logHour가 null인 경우
     */
    @Override
    public Optional<HourlyTelemetryStatResponse> findByLocationAndLogHour(Long locationId, OffsetDateTime logHour) {

        if (locationId == null) {
            throw new InvalidRequestException("locationId 는 필수값입니다.");
        }

        if (logHour == null) {
            throw new InvalidRequestException("logHour 값은 필수값입니다.");
        }

        return hourlyTelemetryStatRepository.findByLocationIdAndLogHour(locationId, logHour)
                .map(HourlyTelemetryStatResponse::from);
    }

    @Transactional
    @Override
    public void deleteByLocation(Long locationId) {
        if (locationId == null) {
            throw new InvalidRequestException("locationId는 필수값입니다.");
        }
        hourlyTelemetryStatRepository.deleteByLocationId(locationId);
        log.info("텔레메트리 집계 일괄 삭제 - locationId:{}", locationId);
    }

    @Transactional
    @Override
    public void deleteByLocations(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            throw new InvalidRequestException("locationIds는 필수값입니다.");
        }
        hourlyTelemetryStatRepository.deleteByLocationIdIn(locationIds);
        log.info("텔레메트리 집계 일괄 삭제 - locationIds:{}", locationIds);
    }


    /**
     * from~to 기간 내 시간별 통계를 재집계한다. 시간별로 저장된 metrics_avg/max/min은 "평균의 평균"/"최고 중 최고"/"최저 중 최저"로, actuator_on_minutes는
     * 합산으로 재집계한다.
     *
     * @param locationId 위치 ID(필수)
     * @param from       집계 시작(포함)
     * @param to         집계 종료(포함)
     * @return 기간 재집계 결과
     * @throws InvalidRequestException locationId가 null인 경우
     */
    @Override
    public PeriodTelemetrySummary summarizePeriod(Long locationId, OffsetDateTime from, OffsetDateTime to) {

        if (locationId == null) {
            throw new InvalidRequestException("locationId는 필수값입니다.");
        }

        List<HourlyTelemetryStat> stats = hourlyTelemetryStatRepository.search(locationId, from, to);

        Map<String, List<Double>> avgSamples = new HashMap<>();
        Map<String, Double> maxByMetric = new HashMap<>();
        Map<String, Double> minByMetric = new HashMap<>();
        Map<String, Double> actuatorSum = new HashMap<>();

        for (HourlyTelemetryStat stat : stats) {
            parseJson(stat.getMetricsAvg()).forEach((key, value) ->
                    avgSamples.computeIfAbsent(key, k -> new ArrayList<>()).add(value));

            parseJson(stat.getMetricsMax()).forEach((key, value) ->
                    maxByMetric.merge(key, value, Math::max));

            parseJson(stat.getMetricsMin()).forEach((key, value) ->
                    minByMetric.merge(key, value, Math::min));

            parseJson(stat.getActuatorOnMinutes()).forEach((key, value) ->
                    actuatorSum.merge(key, value, Double::sum));
        }

        Map<String, Double> avgByMetrics = avgSamples.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry ->
                        average(entry.getValue())));

        log.info("기간별 통계 재집계 - locationId:{}, from:{}, to:{}, 대상 시간 수:{}",
                locationId, from, to, stats.size());

        return new PeriodTelemetrySummary(locationId, from, to, avgByMetrics, maxByMetric, minByMetric, actuatorSum);
    }

    @Override
    public List<Long> findDistinctLocationIds(OffsetDateTime from, OffsetDateTime to) {
        return hourlyTelemetryStatRepository.findDistinctLocationIds(from, to);
    }

    /**
     * hourly_telemetry_stats의 JSONB 컬럼(문자열)을 Map으로 파싱한다. null이면 빈 맵(집계 대상 없음)으로 처리한다.
     */
    private Map<String, Double> parseJson(String json) {
        if (json == null) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("메트릭 JSON 파싱 실패: " + json, e);
        }
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

}
