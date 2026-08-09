package com.insighton.ai.domain.telemetrystats.service;

import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface HourlyTelemetryStatService {

    /**
     * 위치 ID(필수), 기간(선택) 조건에 따른 시간별 통계 목록 조회.
     *
     * @param locationId 위치 ID(필수)
     * @param from       조회 시작 시각(선택)
     * @param to         조회 종료 시각(선택)
     * @return 시간별 통계 목록 응답
     */
    List<HourlyTelemetryStatResponse> findHourlyTelemetryStats(
            Long locationId, OffsetDateTime from, OffsetDateTime to);

    /**
     * 시간별 통계 신규 생성, 저장 전 Bean Validation 기반 요청값 유효성 검증 수행.
     *
     * @param request 시간별 통계 생성 요청
     * @return 저장된 시간별 통계 응답
     */
    HourlyTelemetryStatResponse create(HourlyTelemetryStatCreateRequest request);

    /**
     * 위치 ID·집계 시간대 복합 키 기준 단건 조회 (내부용 — 정각 집계 배치의 중복 집계 방지 체크에 사용).
     *
     * @param locationId 위치 ID(필수)
     * @param logHour    집계 시간대(필수)
     * @return 존재하면 시간별 통계 응답, 없으면(=아직 집계 전) 빈 Optional
     */
    Optional<HourlyTelemetryStatResponse> findByLocationAndLogHour(Long locationId, OffsetDateTime logHour);

    void deleteByLocation(Long locationId);

    /**
     * 위치 ID 목록 기준 시간별 통계 일괄 삭제 (Core 그룹 삭제 이벤트 수신 시 호출되는 내부용 — 이 테이블은 group_id가 없어 location_id 목록으로 처리).
     *
     * @param locationIds 위치 ID 목록
     */
    void deleteByLocations(List<Long> locationIds);

    /**
     * from~to 기간 내 시간별 통계를 재집계한다. 일간 조회(대시보드), 주간/월간 리포트 생성 배치가 공통으로 사용한다.
     *
     * @param locationId 위치 ID(필수)
     * @param from       집계 시작(포함)
     * @param to         집계 종료(포함)
     * @return 기간 재집계 결과
     */
    PeriodTelemetrySummary summarizePeriod(Long locationId, OffsetDateTime from, OffsetDateTime to);

    /**
     * from~to 기간 내 hourly_telemetry_stats에 실제로 데이터가 존재하는 location_id 목록을 조회한다.
     * 리포트 생성 배치가 Core 조회 없이 집계 대상을 확보하는 데 사용한다.
     *
     * @param from 조회 시작(포함)
     * @param to   조회 종료(포함)
     * @return distinct location_id 목록
     */
    List<Long> findDistinctLocationIds(OffsetDateTime from, OffsetDateTime to);
}
