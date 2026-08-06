package com.insighton.ai.coreapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.insighton.ai.coreapi.domain.AutoControlMode;

/**
 * Core의 위치 조회 응답(GET /internal/locations/{location-id}) — groupId 기반 멤버십 검증, 리포트 제목 조립,
 * 제안 생성 시 auto_control_mode(SUGGESTION/AI_DIRECT) 판단에 쓰인다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationResponse(
        Long locationId,
        String locationName,
        Long groupId,
        AutoControlMode autoControlMode
) {
}