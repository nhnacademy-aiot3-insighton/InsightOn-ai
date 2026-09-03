package com.insighton.ai.domain.report.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.FlowDraftRequester;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorRunLogResponse;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import com.insighton.ai.domain.flow.FlowActionPromptBuilder;
import com.insighton.ai.domain.report.dto.FlowActionDecision;
import com.insighton.ai.domain.report.dto.FlowActionDecisions;
import com.insighton.ai.domain.report.dto.ReportCreateRequest;
import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportGenerationSchedulerTest {

    @Mock
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    @Mock
    private EngineAlertService engineAlertService;

    @Mock
    private SuggestionLogService suggestionLogService;

    @Mock
    private CoreClient coreClient;

    @Mock
    private ReportService reportService;

    @Mock
    private FlowDraftRequester flowDraftRequester;

    @Mock
    private FlowActionPromptBuilder flowActionPromptBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private ReportGenerationScheduler reportGenerationScheduler;

    private static final OffsetDateTime PERIOD_START = OffsetDateTime.parse("2026-08-03T00:00:00+09:00");
    private static final OffsetDateTime PERIOD_END = OffsetDateTime.parse("2026-08-09T23:00:00+09:00");
    private static final OffsetDateTime PREV_START = OffsetDateTime.parse("2026-07-27T00:00:00+09:00");
    private static final OffsetDateTime PREV_END = OffsetDateTime.parse("2026-08-02T23:00:00+09:00");

    private void stubChatClient() {
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
        given(callResponseSpec.content()).willReturn("생성된 리포트 본문");
    }

    private PeriodTelemetrySummary summary(Map<String, Double> avg) {
        return new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END, avg, Map.of(), Map.of(), Map.of(), Map.of());
    }

    private Report savedReport(Long reportId) {
        Report report = Report.builder()
                .groupId(5L).locationId(42L).title("title").reportType(ReportType.WEEKLY).content("content")
                .build();
        ReflectionTestUtils.setField(report, "reportId", reportId);
        return report;
    }

    private void stubCommonData(PeriodTelemetrySummary current, PeriodTelemetrySummary previous) {
        stubCommonData(current, previous, List.of());
    }

    private void stubCommonData(PeriodTelemetrySummary current, PeriodTelemetrySummary previous,
                                List<ActuatorRunLogResponse> actuatorLogs) {
        stubChatClient();
        given(coreClient.getLocation(42L)).willReturn(
                new LocationResponse(42L, "3층 회의실", 5L, AutoControlMode.SUGGESTION));
        given(hourlyTelemetryStatService.summarizePeriod(42L, PERIOD_START, PERIOD_END)).willReturn(current);
        given(hourlyTelemetryStatService.summarizePeriod(42L, PREV_START, PREV_END)).willReturn(previous);
        given(engineAlertService.summarizePeriod(42L, PERIOD_START, PERIOD_END))
                .willReturn(new EngineAlertSummary(0, 0, List.of()));
        given(suggestionLogService.summarizePeriod(42L, PERIOD_START, PERIOD_END))
                .willReturn(new SuggestionSummary(0, 0, 0, 0));
        given(coreClient.getActuatorRunLogs(eq(List.of(42L)), eq(PERIOD_START), eq(PERIOD_END)))
                .willReturn(actuatorLogs);
        given(reportService.createReport(any())).willReturn(savedReport(100L));
    }

    @Test
    void generateOneReport_정상_흐름이면_LLM_응답을_리포트로_저장한다() {
        PeriodTelemetrySummary current = summary(Map.of("temperature", 24.0));
        PeriodTelemetrySummary previous = summary(Map.of("temperature", 22.0));
        stubCommonData(current, previous);

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<ReportCreateRequest> captor = ArgumentCaptor.forClass(ReportCreateRequest.class);
        verify(reportService).createReport(captor.capture());
        ReportCreateRequest request = captor.getValue();
        assertThat(request.groupId()).isEqualTo(5L);
        assertThat(request.locationId()).isEqualTo(42L);
        assertThat(request.reportType()).isEqualTo(ReportType.WEEKLY);
        assertThat(request.content()).isEqualTo("생성된 리포트 본문");
    }

    @Test
    void generateOneReport_WEEKLY_제목은_월_주차_위치명_형식이다() {
        stubCommonData(summary(Map.of()), summary(Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<ReportCreateRequest> captor = ArgumentCaptor.forClass(ReportCreateRequest.class);
        verify(reportService).createReport(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("8월 1주차 3층 회의실 리포트");
    }

    @Test
    void generateOneReport_MONTHLY_제목은_월_월간_위치명_형식이다() {
        stubCommonData(summary(Map.of()), summary(Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<ReportCreateRequest> captor = ArgumentCaptor.forClass(ReportCreateRequest.class);
        verify(reportService).createReport(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("8월 월간 3층 회의실 리포트");
    }

    @Test
    void generateOneReport_액추에이터_조작_이력이_없으면_프롬프트에_조작_이력_섹션이_빠진다() {
        stubCommonData(summary(Map.of()), summary(Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("## 액추에이터 조작 이력");
    }

    @Test
    void generateOneReport_액추에이터_조작_이력이_있으면_평균_설정온도와_주체별_비율을_계산한다() {
        stubCommonData(summary(Map.of()), summary(Map.of()), List.of(
                new ActuatorRunLogResponse(42L, 1L, "AIRCON", "SET_TEMPERATURE", "22", "USER", PERIOD_START),
                new ActuatorRunLogResponse(42L, 1L, "AIRCON", "SET_TEMPERATURE", "24", "USER", PERIOD_START)
        ));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("## 액추에이터 조작 이력");
        assertThat(prompt).contains("총 2회");
        assertThat(prompt).contains("평균 설정값 23.0");
        assertThat(prompt).contains("USER 조작 비율: 100%");
    }

    @Test
    void generateOneReport_MONTHLY_시간대별_패턴이_프롬프트에_포함된다() {
        PeriodTelemetrySummary current = summary(Map.of("temperature", 24.0));
        stubCommonData(current, summary(Map.of()));
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(
                new HourlyPeakPattern("temperature", 14, 30.0, 24.0, 25.0)));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("temperature: 14시경 평균 30.0 (기간 평균 24.0 대비 +25.0%)");
    }

    @Test
    void generateOneReport_MONTHLY_유의미한_패턴이_없으면_시간대별_패턴_섹션이_생략된다() {
        PeriodTelemetrySummary current = summary(Map.of("temperature", 24.0));
        stubCommonData(current, summary(Map.of()));
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of());

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("## 시간대별 패턴");
    }

    @Test
    void generateOneReport_그룹_평균_대비_15퍼센트_이상_차이나면_비교_섹션에_포함된다() {
        PeriodTelemetrySummary current = summary(Map.of("temperature", 30.0));
        stubCommonData(current, summary(Map.of()));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(99L, "옆 회의실", 5L, AutoControlMode.SUGGESTION)));
        given(hourlyTelemetryStatService.summarizePeriod(99L, PERIOD_START, PERIOD_END))
                .willReturn(summary(Map.of("temperature", 20.0)));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("## 그룹 내 다른 위치 대비");
        assertThat(prompt).contains("temperature: 이 위치 30.0 vs 그룹 평균 20.0 (+50.0%)");
    }

    @Test
    void generateOneReport_그룹_평균과_차이가_15퍼센트_미만이면_비교_섹션이_생략된다() {
        PeriodTelemetrySummary current = summary(Map.of("temperature", 21.0));
        stubCommonData(current, summary(Map.of()));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(99L, "옆 회의실", 5L, AutoControlMode.SUGGESTION)));
        given(hourlyTelemetryStatService.summarizePeriod(99L, PERIOD_START, PERIOD_END))
                .willReturn(summary(Map.of("temperature", 20.0)));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("## 그룹 내 다른 위치 대비");
    }

    @Test
    void generateOneReport_액추에이터가_없는_위치는_그룹_평균에서_제외된다() {
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of(), Map.of(), Map.of(), Map.of("AIRCON", 100.0), Map.of());
        stubCommonData(current, summary(Map.of()));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(98L, "AIRCON_있음", 5L, AutoControlMode.SUGGESTION),
                new LocationResponse(99L, "AIRCON_없음", 5L, AutoControlMode.SUGGESTION)));
        given(hourlyTelemetryStatService.summarizePeriod(98L, PERIOD_START, PERIOD_END)).willReturn(
                new PeriodTelemetrySummary(98L, PERIOD_START, PERIOD_END,
                        Map.of("temperature", 22.0), Map.of(), Map.of(), Map.of("AIRCON", 90.0), Map.of()));
        given(hourlyTelemetryStatService.summarizePeriod(99L, PERIOD_START, PERIOD_END)).willReturn(
                new PeriodTelemetrySummary(99L, PERIOD_START, PERIOD_END,
                        Map.of("temperature", 22.0), Map.of(), Map.of(), Map.of(), Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        // AIRCON이 없는 99L을 0으로 잡아 평균 냈다면 (90+0)/2=45라 100분과 122% 차이로 섹션에 나타났을 것.
        // 98L만으로 평균 90분을 내면 100분과 11.1% 차이라 임계치(15%) 밑이라 섹션 자체가 생략돼야 한다.
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("## 그룹 내 다른 위치 대비");
    }

    @Test
    void generateOneReport_센서_없이_액추에이터만_있는_위치도_액추에이터_비교에_포함된다() {
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of(), Map.of(), Map.of(), Map.of("AIRCON", 100.0), Map.of());
        stubCommonData(current, summary(Map.of()));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(99L, "센서_없는_위치", 5L, AutoControlMode.SUGGESTION)));
        // metricsAvg는 비어있지만 actuatorOnMinutes엔 데이터가 있음 - 예전엔 metricsAvg만 보고 통째로
        // 걸러져서 이 위치의 액추에이터 데이터까지 같이 사라졌었음
        given(hourlyTelemetryStatService.summarizePeriod(99L, PERIOD_START, PERIOD_END)).willReturn(
                new PeriodTelemetrySummary(99L, PERIOD_START, PERIOD_END,
                        Map.of(), Map.of(), Map.of(), Map.of("AIRCON", 60.0), Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("AIRCON 가동시간: 이 위치 100.0분 vs 그룹 평균 60.0분 (+66.7%)");
    }

    @Test
    void generateOneReport_개선_제안에_기대효과_서술_지침이_포함된다() {
        stubCommonData(summary(Map.of()), summary(Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("개선 제안마다 기대 효과를 함께 제시하세요");
        assertThat(prompt).contains("검증 불가능한 정확한 수치");
    }

    @Test
    void generateOneReport_지표_나열_섹션은_표로_정리하라는_지침이_포함된다() {
        stubCommonData(summary(Map.of()), summary(Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("마크다운 표로");
    }

    @Test
    void generateWeeklyReports_대상_location이_없으면_아무것도_생성하지_않는다() {
        given(hourlyTelemetryStatService.findDistinctLocationIds(any(), any())).willReturn(List.of());

        reportGenerationScheduler.generateWeeklyReports();

        verify(reportService, never()).createReport(any());
        verify(chatClient, never()).prompt();
    }

    @Test
    void generateWeeklyReports_한_location이_실패해도_나머지는_계속_처리된다() {
        stubChatClient();
        given(hourlyTelemetryStatService.findDistinctLocationIds(any(), any())).willReturn(List.of(1L, 2L));
        given(coreClient.getLocation(1L)).willThrow(new RuntimeException("Core 호출 실패"));
        given(coreClient.getLocation(2L)).willReturn(new LocationResponse(2L, "2층 로비", 5L,
                AutoControlMode.SUGGESTION));
        given(hourlyTelemetryStatService.summarizePeriod(eq(2L), any(), any())).willReturn(summary(Map.of()));
        given(engineAlertService.summarizePeriod(eq(2L), any(), any()))
                .willReturn(new EngineAlertSummary(0, 0, List.of()));
        given(suggestionLogService.summarizePeriod(eq(2L), any(), any()))
                .willReturn(new SuggestionSummary(0, 0, 0, 0));
        given(coreClient.getActuatorRunLogs(eq(List.of(2L)), any(), any())).willReturn(List.of());
        given(reportService.createReport(any())).willReturn(savedReport(101L));

        reportGenerationScheduler.generateWeeklyReports();

        verify(reportService, times(1)).createReport(any());
        verify(coreClient, never()).getActuatorRunLogs(eq(List.of(1L)), any(), any());
    }

    @Test
    void generateOneReport_MONTHLY_LLM이_자동화를_추천하면_flow_초안을_요청한다() {
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of("co2", 800.0), Map.of(), Map.of(), Map.of("VENTILATION_FAN", 30.0), Map.of());
        stubCommonData(current, summary(Map.of()));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5);
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(pattern));
        given(flowActionPromptBuilder.build(any(), any())).willReturn("flow 판단 프롬프트");
        given(callResponseSpec.entity(FlowActionDecisions.class)).willReturn(new FlowActionDecisions(List.of(
                new FlowActionDecision("co2", true, ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON"))));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        verify(flowDraftRequester).requestDraft(5L, 42L, "8월 월간 3층 회의실 리포트 #100", pattern,
                new ActuatorAction(ActuatorType.VENTILATION_FAN, "POWER_STATUS", "ON"));
    }

    @Test
    void generateOneReport_업무시간_밖_피크는_리포트에는_남지만_flow_자동화_대상에서는_제외된다() {
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of("co2", 800.0), Map.of(), Map.of(), Map.of("VENTILATION_FAN", 30.0), Map.of());
        stubCommonData(current, summary(Map.of()));
        HourlyPeakPattern pattern = new HourlyPeakPattern("co2", 3, 1100.0, 800.0, 37.5);
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(pattern));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("co2: 3시경");

        verify(callResponseSpec, never()).entity(FlowActionDecisions.class);
        verify(flowDraftRequester, never()).requestDraft(any(), any(), any(), any(), any());
    }

    @Test
    void generateOneReport_LLM이_자동화를_추천하지_않으면_flow_초안_요청에서_제외된다() {
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of("co2", 800.0), Map.of(), Map.of(), Map.of("VENTILATION_FAN", 30.0), Map.of());
        stubCommonData(current, summary(Map.of()));
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(
                new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5)));
        given(flowActionPromptBuilder.build(any(), any())).willReturn("flow 판단 프롬프트");
        given(callResponseSpec.entity(FlowActionDecisions.class)).willReturn(new FlowActionDecisions(List.of(
                new FlowActionDecision("co2", false, null, null, null))));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        verify(flowDraftRequester, never()).requestDraft(any(), any(), any(), any(), any());
    }

    @Test
    void generateOneReport_LLM이_위치에_없는_액추에이터를_골라도_서버사이드에서_걸러진다() {
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of("co2", 800.0), Map.of(), Map.of(), Map.of("VENTILATION_FAN", 30.0), Map.of());
        stubCommonData(current, summary(Map.of()));
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(
                new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5)));
        // 이 위치엔 AIRCON이 없는데(actuatorOnMinutes엔 VENTILATION_FAN만) LLM이 AIRCON을 골랐다고 가정
        given(flowActionPromptBuilder.build(any(), any())).willReturn("flow 판단 프롬프트");
        given(callResponseSpec.entity(FlowActionDecisions.class)).willReturn(new FlowActionDecisions(List.of(
                new FlowActionDecision("co2", true, ActuatorType.AIRCON, "POWER_STATUS", "ON"))));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        verify(flowDraftRequester, never()).requestDraft(any(), any(), any(), any(), any());
    }

    @Test
    void generateOneReport_이_위치에_액추에이터가_없으면_LLM_호출_자체를_건너뛴다() {
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of("co2", 800.0), Map.of(), Map.of(), Map.of(), Map.of());
        stubCommonData(current, summary(Map.of()));
        given(hourlyTelemetryStatService.extractPeakPatterns(current)).willReturn(List.of(
                new HourlyPeakPattern("co2", 14, 1100.0, 800.0, 37.5)));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        verify(flowDraftRequester, never()).requestDraft(any(), any(), any(), any(), any());
        verify(callResponseSpec, never()).entity(FlowActionDecisions.class);
    }

    @Test
    void generateOneReport_WEEKLY는_flow_초안을_요청하지_않는다() {
        stubCommonData(summary(Map.of("co2", 800.0)), summary(Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.WEEKLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        verify(flowDraftRequester, never()).requestDraft(any(), any(), any(), any(), any());
        verify(hourlyTelemetryStatService, never()).extractPeakPatterns(any());
    }
}
