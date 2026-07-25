package com.insighton.ai.telemetrystats.service.impl;

import com.insighton.ai.exception.InvalidRequestException;
import com.insighton.ai.telemetrystats.domain.HourlyTelemetryStat;
import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.telemetrystats.repository.HourlyTelemetryStatRepository;
import com.insighton.ai.telemetrystats.service.HourlyTelemetryStatService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * 그룹 ID(필수), 위치 ID·기간(선택) 조건에 따른 시간별 통계 목록 조회.
     *
     * @param groupId    그룹 ID(필수)
     * @param locationId 위치 ID(선택)
     * @param from       조회 시작 시각(선택)
     * @param to         조회 종료 시각(선택)
     * @return 시간별 통계 목록 응답
     * @throws InvalidRequestException groupId가 null인 경우
     */
    @Override
    public List<HourlyTelemetryStatResponse> findHourlyTelemetryStats(Long groupId, Long locationId,
                                                                      OffsetDateTime from, OffsetDateTime to) {

        if (groupId == null) {
            throw new InvalidRequestException("groupId 는 필수값입니다.");
        }

        List<HourlyTelemetryStat> stats = hourlyTelemetryStatRepository.search(groupId, locationId, from, to);

        log.info("시간별 통계 조회 - groupId:{}, locationId:{}, from:{}, to:{}, size:{}",
                groupId, locationId, from, to, stats.size());
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
    @Override
    public HourlyTelemetryStatResponse create(HourlyTelemetryStatCreateRequest request) {

        Set<ConstraintViolation<HourlyTelemetryStatCreateRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        HourlyTelemetryStat stat = HourlyTelemetryStat.builder()
                .groupId(request.groupId())
                .locationId(request.locationId())
                .logHour(request.logHour())
                .metricsAvg(request.metricsAvg())
                .metricsMax(request.metricsMax())
                .metricsMin(request.metricsMin())
                .actuatorOnMinutes(request.actuatorOnMinutes())
                .build();

        HourlyTelemetryStat saveStat = hourlyTelemetryStatRepository.save(stat);

        log.info("시간별 통계 저장 - groupId:{}, locationId:{}, logHour:{}",
                saveStat.getGroupId(), saveStat.getLocationId(), saveStat.getLogHour());

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
}
