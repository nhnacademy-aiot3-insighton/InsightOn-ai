package com.insighton.ai.adapter.client.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.WeatherResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class WeatherChatToolTest {

    @Mock
    private CoreClient coreClient;

    @InjectMocks
    private WeatherChatTool weatherChatTool;

    @Test
    void getWeather_toolContext의_groupId로_조회해_한줄_문자열로_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        WeatherResponse response = new WeatherResponse(
                23.5, "맑음", "없음", 45.0, 27.0, 18.0, "보통", 24.0, "구름많음", "비", null, 25.0, 18.0);
        given(coreClient.getWeather(5L)).willReturn(response);

        String result = weatherChatTool.getWeather(toolContext);

        assertThat(result).isEqualTo(
                "현재기온=23.5 | 하늘상태=맑음 | 강수형태=없음 | 습도=45.0 | 최고기온=27.0 | 최저기온=18.0 | "
                        + "미세먼지등급=보통 | 1시간후예보기온=24.0 | 예보하늘상태=구름많음 | 예보강수형태=비 | "
                        + "4~10일후중기예보평균최고기온=25.0 | 4~10일후중기예보평균최저기온=18.0");
    }

    @Test
    void getWeather_null인_필드는_정보없음으로_표시한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        WeatherResponse response = new WeatherResponse(
                23.5, "맑음", "없음", 45.0, 27.0, 18.0, null, 24.0, "구름많음", "비", null, null, null);
        given(coreClient.getWeather(5L)).willReturn(response);

        String result = weatherChatTool.getWeather(toolContext);

        assertThat(result).contains("미세먼지등급=정보없음");
    }
}
