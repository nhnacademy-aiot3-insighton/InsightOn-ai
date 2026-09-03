package com.insighton.ai.adapter.client.tool;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.WeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherChatTool {

    private final CoreClient coreClient;


    @Tool(description = "현재 그룹 위치의 실외 날씨/미세먼지 정보를 조회한다. '오늘/지금 날씨'뿐 아니라 "
            + "'이번 주 날씨 전망'처럼 며칠 뒤 기온 흐름을 묻는 질문에도 이 툴의 중기예보평균기온 값으로 답한다. "
            + "결과는 '현재기온 | 하늘상태 | 강수형태 | 습도 | 최고/최저기온 | 미세먼지등급 | "
            + "1시간후예보기온 | 예보하늘상태 | 예보강수형태 | 4~10일후중기예보평균최고기온 | 4~10일후중기예보평균최저기온' 형식이다.")
    public String getWeather(ToolContext toolContext) {

        Long groupId = (Long) toolContext.getContext().get("groupId");
        WeatherResponse weather = coreClient.getWeather(groupId);

        return "현재기온=%s | 하늘상태=%s | 강수형태=%s | 습도=%s | 최고기온=%s | 최저기온=%s | 미세먼지등급=%s | 1시간후예보기온=%s | 예보하늘상태=%s | 예보강수형태=%s | 4~10일후중기예보평균최고기온=%s | 4~10일후중기예보평균최저기온=%s".formatted(
                orNone(weather.temperature()), orNone(weather.skyStatus()), orNone(weather.precipitationType()),
                orNone(weather.humidity()), orNone(weather.maxTemp()), orNone(weather.minTemp()),
                orNone(weather.dustGrade()), orNone(weather.forecastTemperature()),
                orNone(weather.forecastSkyStatus()), orNone(weather.forecastPrecipitationType()),
                orNone(weather.midTermAvgMaxTemp()), orNone(weather.midTermAvgMinTemp()));
    }

    private static String orNone(Object value) {
        return value != null ? String.valueOf(value) : "정보없음";
    }
}
