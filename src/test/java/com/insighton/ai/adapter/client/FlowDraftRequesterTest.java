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

    private static final String REPORT_TITLE = "8월 월간 3층 회의실 리포트";

    @Mock
    private RuleEngineClient ruleEngineClient;

    @InjectMocks
    private FlowDraftRequester flowDraftRequester;

    @Test
    void requestDraft_요청을_올바르게_조립해서_호출한다() {
        given(ruleEngineClient.createFlowDraft(anyLong(), any()))
                .willReturn(new FlowDraftResponse(128L, "INACTIVE"));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        flowDraftRequester.requestDraft(5L, 42L, 231L, REPORT_TITLE, pattern, action);

        ArgumentCaptor<Long> groupIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<FlowDraftCreateRequest> requestCaptor = ArgumentCaptor.forClass(FlowDraftCreateRequest.class);
        verify(ruleEngineClient).createFlowDraft(groupIdCaptor.capture(), requestCaptor.capture());

        assertThat(groupIdCaptor.getValue()).isEqualTo(5L);

        FlowDraftCreateRequest request = requestCaptor.getValue();
        assertThat(request.locationId()).isEqualTo(42L);
        assertThat(request.name()).isEqualTo("[AI] co2 예방 자동화 (8월 월간 3층 회의실 리포트 #231)");
        assertThat(request.description())
                .startsWith("[AI 자동 생성] 8월 월간 3층 회의실 리포트 기준")
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
        given(ruleEngineClient.createFlowDraft(anyLong(), any()))
                .willReturn(new FlowDraftResponse(1L, "INACTIVE"));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 0, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        flowDraftRequester.requestDraft(5L, 42L, 231L, REPORT_TITLE, pattern, action);

        ArgumentCaptor<FlowDraftCreateRequest> requestCaptor = ArgumentCaptor.forClass(FlowDraftCreateRequest.class);
        verify(ruleEngineClient).createFlowDraft(anyLong(), requestCaptor.capture());

        assertThat(requestCaptor.getValue().nodes().get(0).configuration())
                .containsEntry("cron", "0 45 23 * * SUN-THU");
    }

    @Test
    void requestDraft_reportId가_다르면_같은_위치_같은_지표라도_이름이_달라진다() {
        given(ruleEngineClient.createFlowDraft(anyLong(), any()))
                .willReturn(new FlowDraftResponse(1L, "INACTIVE"));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        flowDraftRequester.requestDraft(5L, 42L, 231L, REPORT_TITLE, pattern, action);
        flowDraftRequester.requestDraft(5L, 42L, 299L, "9월 월간 3층 회의실 리포트", pattern, action);

        ArgumentCaptor<FlowDraftCreateRequest> requestCaptor = ArgumentCaptor.forClass(FlowDraftCreateRequest.class);
        verify(ruleEngineClient, times(2)).createFlowDraft(anyLong(), requestCaptor.capture());

        assertThat(requestCaptor.getAllValues())
                .extracting(FlowDraftCreateRequest::name)
                .containsExactly(
                        "[AI] co2 예방 자동화 (8월 월간 3층 회의실 리포트 #231)",
                        "[AI] co2 예방 자동화 (9월 월간 3층 회의실 리포트 #299)");
    }

    @Test
    void requestDraft_RuleEngine_호출이_실패해도_예외를_던지지_않는다() {
        given(ruleEngineClient.createFlowDraft(anyLong(), any()))
                .willThrow(new RuntimeException("Rule Engine 연결 실패"));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        ActuatorAction action = new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON");

        assertThatCode(() -> flowDraftRequester.requestDraft(5L, 42L, 231L, REPORT_TITLE, pattern, action))
                .doesNotThrowAnyException();
    }
}
