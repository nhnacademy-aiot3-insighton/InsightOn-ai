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
import com.insighton.ai.adapter.client.dto.ActuatorRunLogResponse;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import com.insighton.ai.domain.report.dto.ReportCreateRequest;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
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
        PeriodTelemetrySummary current = new PeriodTelemetrySummary(42L, PERIOD_START, PERIOD_END,
                Map.of("temperature", 24.0), Map.of(), Map.of(), Map.of(),
                Map.of("temperature", Map.of(14, 27.5, 3, 21.0)));
        stubCommonData(current, summary(Map.of()));

        reportGenerationScheduler.generateOneReport(ReportType.MONTHLY, 42L, PERIOD_START, PERIOD_END, PREV_START,
                PREV_END);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("temperature: 14시경 평균 27.5로 가장 높음");
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

        reportGenerationScheduler.generateWeeklyReports();

        verify(reportService, times(1)).createReport(any());
        verify(coreClient, never()).getActuatorRunLogs(eq(List.of(1L)), any(), any());
    }
}
