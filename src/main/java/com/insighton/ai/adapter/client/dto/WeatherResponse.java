package com.insighton.ai.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Core의 날씨 조회 응답(GET /internal/groups/{group-id}/weather) — 제안 생성 시 참고하는 실외 환경 정보.
 * 기상청 실황(현재값)·초단기예보(1시간 후 forecast* 필드)·단기예보(오늘 최고/최저)·중기기온예보(4~10일 후
 * midTerm* 필드)를 Core가 조합해 내려준다. midTerm*은 순간값이 아니라 여러 날의 평균이라, 관리 중인
 * 자동화가 계절적으로 여전히 맞는지 판단할 때 하루짜리 이상기온에 덜 흔들리는 신호로 쓴다.
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
        Double forecastHumidity,
        Double midTermAvgMaxTemp,
        Double midTermAvgMinTemp
) {
}