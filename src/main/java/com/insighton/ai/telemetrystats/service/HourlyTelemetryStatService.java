package com.insighton.ai.telemetrystats.service;

import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
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
}
