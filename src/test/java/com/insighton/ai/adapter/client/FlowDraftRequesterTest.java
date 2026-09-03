package com.insighton.ai.adapter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.adapter.client.dto.FlowDraftCreateRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftLinkRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftNodeRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftResponse;
import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowDraftRequesterTest {

    private static final String SOURCE_DESCRIPTION = "8월 월간 3층 회의실 리포트 #231";

    @Mock
    private RuleEngineClient ruleEngineClient;

    @InjectMocks
    private FlowDraftRequester flowDraftRequester;

    @Test
    void requestDraft_요청을_올바르게_조립해서_호출한다() {
        given(ruleEngineClient.createAiDraft(anyLong(), any()))
                .willReturn(new FlowDraftResponse(128L, "INACTIVE", null));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        flowDraftRequester.requestDraft(5L, 42L, SOURCE_DESCRIPTION, pattern, action);

        ArgumentCaptor<Long> groupIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<FlowDraftCreateRequest> requestCaptor = ArgumentCaptor.forClass(FlowDraftCreateRequest.class);
        verify(ruleEngineClient).createAiDraft(groupIdCaptor.capture(), requestCaptor.capture());

        assertThat(groupIdCaptor.getValue()).isEqualTo(5L);

        FlowDraftCreateRequest request = requestCaptor.getValue();
        assertThat(request.locationId()).isEqualTo(42L);
        assertThat(request.name()).isEqualTo("[AI] co2 예방 자동화");
        assertThat(request.description())
                .startsWith("[AI 자동 생성] 8월 월간 3층 회의실 리포트 #231 기준")
                .contains("co2");

        assertThat(request.nodes()).containsExactly(
                new FlowDraftNodeRequest("schedule", "SCHEDULE", Map.of("cron", "0 45 13 * * MON-FRI")),
                new FlowDraftNodeRequest("actuatorControl", "ACTUATOR_CONTROL",
                        Map.of("actuatorType", "VENTILATION_FAN", "command", "power", "commandValue", "ON")));
        assertThat(request.links()).containsExactly(
                new FlowDraftLinkRequest("schedule", "actuatorControl", "out", "in"));
    }

    @Test
    void requestDraft_피크시간이_0시면_전날_23시_45분_SUN_THU_cron으로_조립된다() {
        given(ruleEngineClient.createAiDraft(anyLong(), any()))
                .willReturn(new FlowDraftResponse(1L, "INACTIVE", null));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 0, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        flowDraftRequester.requestDraft(5L, 42L, SOURCE_DESCRIPTION, pattern, action);

        ArgumentCaptor<FlowDraftCreateRequest> requestCaptor = ArgumentCaptor.forClass(FlowDraftCreateRequest.class);
        verify(ruleEngineClient).createAiDraft(anyLong(), requestCaptor.capture());

        assertThat(requestCaptor.getValue().nodes().get(0).configuration())
                .containsEntry("cron", "0 45 23 * * SUN-THU");
    }

    @Test
    void requestDraft_sourceDescription이_달라도_이름은_같다() {
        // 이름의 유일성은 더 이상 AI 쪽 책임이 아님 - Rule Engine의 createAiDraft가 이름이 달라도
        // 실질적으로 같은 동작(트리거 시각·액추에이터 명령)이면 근사 중복으로 판단해 대체해준다.
        given(ruleEngineClient.createAiDraft(anyLong(), any()))
                .willReturn(new FlowDraftResponse(1L, "INACTIVE", null));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        flowDraftRequester.requestDraft(5L, 42L, SOURCE_DESCRIPTION, pattern, action);
        flowDraftRequester.requestDraft(5L, 42L, "챗봇 요청", pattern, action);

        ArgumentCaptor<FlowDraftCreateRequest> requestCaptor = ArgumentCaptor.forClass(FlowDraftCreateRequest.class);
        verify(ruleEngineClient, times(2)).createAiDraft(anyLong(), requestCaptor.capture());

        assertThat(requestCaptor.getAllValues())
                .extracting(FlowDraftCreateRequest::name)
                .containsExactly("[AI] co2 예방 자동화", "[AI] co2 예방 자동화");
        assertThat(requestCaptor.getAllValues())
                .extracting(FlowDraftCreateRequest::description)
                .anySatisfy(description -> assertThat(description).contains("챗봇 요청"));
    }

    @Test
    void requestDraft_RuleEngine_호출이_실패해도_예외를_던지지_않는다() {
        given(ruleEngineClient.createAiDraft(anyLong(), any()))
                .willThrow(new RuntimeException("Rule Engine 연결 실패"));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        assertThatCode(() -> flowDraftRequester.requestDraft(5L, 42L, SOURCE_DESCRIPTION, pattern, action))
                .doesNotThrowAnyException();
    }
}
