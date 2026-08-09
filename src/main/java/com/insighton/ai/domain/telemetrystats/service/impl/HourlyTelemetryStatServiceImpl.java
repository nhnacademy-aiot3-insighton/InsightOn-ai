package com.insighton.ai.domain.telemetrystats.service.impl;

import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.telemetrystats.entity.HourlyTelemetryStat;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.repository.HourlyTelemetryStatRepository;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

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
    private final JsonMapper jsonMapper;

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
        // 지표별 · 시간대(0~23시)별 원본 시간별 평균값 리스트. 기간 전체를 하나로 뭉개는 avgSamples와 달리
        // "몇 시에 값이 오르는지"를 나중에 뽑아낼 수 있도록 시간대 차원을 유지한 채로 모아둠.
        Map<String, Map<Integer, List<Double>>> hourlySamples = new HashMap<>();

        for (HourlyTelemetryStat stat : stats) {
            // 이 시간별 통계 row가 하루 중 몇 시(0~23) 데이터인지 추출
            int hourOfDay = stat.getLogHour().getHour();

            parseJson(stat.getMetricsAvg()).forEach((key, value) -> {
                avgSamples.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                // 신규 추가: 같은 값을 시간대별 버킷에도 같이 적재
                hourlySamples.computeIfAbsent(key, k -> new HashMap<>())
                        .computeIfAbsent(hourOfDay, h -> new ArrayList<>()).add(value);
            });

            parseJson(stat.getMetricsMax()).forEach((key, value) ->
                    maxByMetric.merge(key, value, Math::max));

            parseJson(stat.getMetricsMin()).forEach((key, value) ->
                    minByMetric.merge(key, value, Math::min));

            parseJson(stat.getActuatorOnMinutes()).forEach((key, value) ->
                    actuatorSum.merge(key, value, Double::sum));
        }

        // TODO: 시간별 평균의 단순 평균(mean of means)이라 시간마다 원본 샘플 수가 다르면 실제 평균과 어긋날 수 있음.
        //  hourly_telemetry_stats에 시간당 샘플 수(metrics_count) 컬럼을 추가해 가중평균으로 바꾸는 것을 고려할 것 — DB 스키마 변경 필요(ddl-auto=validate).
        Map<String, Double> avgByMetrics = avgSamples.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry ->
                        average(entry.getValue())));

        // hourlySamples(지표 → 시간대 → 원본값 리스트)를 시간대별 평균(지표 → 시간대 → 평균값)으로 축약
        Map<String, Map<Integer, Double>> hourlyAvgByMetric = hourlySamples.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry ->
                        entry.getValue().entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        hourEntry -> average(hourEntry.getValue())))));

        log.info("기간별 통계 재집계 - locationId:{}, from:{}, to:{}, 대상 시간 수:{}",
                locationId, from, to, stats.size());

        return new PeriodTelemetrySummary(locationId, from, to, avgByMetrics, maxByMetric, minByMetric, actuatorSum,
                hourlyAvgByMetric);
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
            return jsonMapper.readValue(json, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("메트릭 JSON 파싱 실패: " + json, e);
        }
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

}
