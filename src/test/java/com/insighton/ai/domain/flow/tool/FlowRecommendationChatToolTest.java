package com.insighton.ai.domain.flow.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.FlowDraftRequester;
import com.insighton.ai.adapter.client.LocationResolver;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.domain.flow.FlowActionPromptBuilder;
import com.insighton.ai.domain.report.dto.FlowActionDecision;
import com.insighton.ai.domain.report.dto.FlowActionDecisions;
import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class FlowRecommendationChatToolTest {

    @Mock
    private LocationResolver locationResolver;

    @Mock
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    @Mock
    private FlowActionPromptBuilder flowActionPromptBuilder;

    @Mock
    private FlowDraftRequester flowDraftRequester;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private FlowRecommendationChatTool flowRecommendationChatTool;

    @BeforeEach
    void setUp() {
        flowRecommendationChatTool = new FlowRecommendationChatTool(
                locationResolver, hourlyTelemetryStatService, flowActionPromptBuilder, flowDraftRequester, chatClient);
    }

    private PeriodTelemetrySummary summary(Map<String, Double> avg, Map<String, Double> actuatorOnMinutes) {
        return new PeriodTelemetrySummary(42L, null, null, avg, Map.of(), Map.of(), actuatorOnMinutes, Map.of());
    }

    private void stubChatClient(FlowActionDecisions decisions) {
        given(flowActionPromptBuilder.build(any(), any())).willReturn("flow 판단 프롬프트");
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
        given(callResponseSpec.entity(FlowActionDecisions.class)).willReturn(decisions);
    }

    @Test
    void createRecommendedFlow_locationName도_없고_context에_locationId도_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));

        String result = flowRecommendationChatTool.createRecommendedFlow(null, toolContext);

        assertThat(result).isEqualTo("이 대화에서 어느 위치를 말하는지 알 수 없어 자동화를 추천할 수 없습니다. "
                + "사용자에게 어느 위치인지 물어보세요.");
    }

    @Test
    void createRecommendedFlow_locationName과_일치하는_위치가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(locationResolver.resolveIdByName(5L, "없는위치")).willReturn(Optional.empty());

        String result = flowRecommendationChatTool.createRecommendedFlow("없는위치", toolContext);

        assertThat(result).isEqualTo("위치를 찾을 수 없습니다: 없는위치");
    }

    @Test
    void createRecommendedFlow_최근_데이터가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(summary(Map.of(), Map.of()));

        String result = flowRecommendationChatTool.createRecommendedFlow(null, toolContext);

        assertThat(result).isEqualTo("최근 30일간 센서 데이터가 없어 자동화를 추천할 수 없습니다.");
    }

    @Test
    void createRecommendedFlow_업무시간_밖_패턴만_있으면_추천할_게_없다고_안내한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        PeriodTelemetrySummary current = summary(Map.of("co2", 800.0), Map.of("VENTILATION_FAN", 30.0));
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(current);
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(
                new HourlyPeakPattern("co2", 3, 1100.0, 800.0, 37.5)));

        String result = flowRecommendationChatTool.createRecommendedFlow(null, toolContext);

        assertThat(result).isEqualTo("최근 데이터에서 업무시간 내 뚜렷한 패턴이나 조작 가능한 액추에이터를 찾지 못해 추천할 자동화가 없습니다.");
        verify(chatClient, never()).prompt();
    }

    @Test
    void createRecommendedFlow_액추에이터가_없으면_추천할_게_없다고_안내한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        PeriodTelemetrySummary current = summary(Map.of("co2", 800.0), Map.of());
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(current);
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(
                new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5)));

        String result = flowRecommendationChatTool.createRecommendedFlow(null, toolContext);

        assertThat(result).isEqualTo("최근 데이터에서 업무시간 내 뚜렷한 패턴이나 조작 가능한 액추에이터를 찾지 못해 추천할 자동화가 없습니다.");
        verify(chatClient, never()).prompt();
    }

    @Test
    void createRecommendedFlow_SUGGESTION_모드면_비활성_상태로_만들었다고_안내한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        PeriodTelemetrySummary current = summary(Map.of("co2", 800.0), Map.of("VENTILATION_FAN", 30.0));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(current);
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(pattern));
        stubChatClient(new FlowActionDecisions(List.of(
                new FlowActionDecision("co2", true, ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON"))));
        given(flowDraftRequester.requestDraft(5L, 42L, "챗봇 요청", pattern,
                new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON")))
                .willReturn(Optional.of("INACTIVE"));

        String result = flowRecommendationChatTool.createRecommendedFlow(null, toolContext);

        assertThat(result)
                .startsWith("1개의 자동화를 만들었습니다.")
                .contains("co2").contains("14시경").contains("VENTILATION_FAN").contains("ON")
                .contains("비활성 상태 - 대시보드에서 활성화 필요");
    }

    @Test
    void createRecommendedFlow_AI_DIRECT_모드면_즉시_활성화됐다고_안내한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        PeriodTelemetrySummary current = summary(Map.of("co2", 800.0), Map.of("VENTILATION_FAN", 30.0));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(current);
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(pattern));
        stubChatClient(new FlowActionDecisions(List.of(
                new FlowActionDecision("co2", true, ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON"))));
        given(flowDraftRequester.requestDraft(5L, 42L, "챗봇 요청", pattern,
                new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON")))
                .willReturn(Optional.of("ACTIVE"));

        String result = flowRecommendationChatTool.createRecommendedFlow(null, toolContext);

        assertThat(result).contains("즉시 활성화됨");
    }

    @Test
    void createRecommendedFlow_LLM이_추천하지_않으면_아무것도_만들지_않는다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        PeriodTelemetrySummary current = summary(Map.of("co2", 800.0), Map.of("VENTILATION_FAN", 30.0));
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(current);
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(
                new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5)));
        stubChatClient(new FlowActionDecisions(List.of(
                new FlowActionDecision("co2", false, null, null, null))));

        String result = flowRecommendationChatTool.createRecommendedFlow(null, toolContext);

        verify(flowDraftRequester, never()).requestDraft(any(), any(), any(), any(), any());
        assertThat(result).isEqualTo("분석 결과 지금 이 위치에 추가로 필요한 자동화가 없습니다.");
    }
}
