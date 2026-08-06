package com.insighton.ai.coreapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Core의 날씨 조회 응답(GET /internal/groups/{group-id}/weather) — 제안 생성 시 참고하는 실외 환경 정보.
 * 기상청 실황(현재값)·초단기예보(1시간 후 forecast* 필드)·단기예보(오늘 최고/최저)를 Core가 조합해 내려준다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeatherResponse(
        Double temperature,
        String skyStatus,
        String precipitationType,
        Double humidity,
        Double maxTemp,
        Double minTemp,
        String dustGrade,
        Double forecastTemperature,
        String forecastSkyStatus,
        String forecastPrecipitationType,
        Double forecastHumidity
) {
}