package com.insighton.ai.domain.report.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.FlowDraftRequester;
import com.insighton.ai.adapter.client.RuleEngineClient;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import com.insighton.ai.domain.flow.FlowActionPromptBuilder;
import com.insighton.ai.domain.report.batch.legacy.LegacyReportGenerationScheduler;
import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * "가상 스레드 병렬화로 배치 시간이 얼마나 줄었나"를 코드 리딩이 아니라 실측으로 증명한다.
 * 레거시 버전은 손으로 요약한 게 아니라 이번 변경 직전 커밋(HEAD)의
 * {@code ReportGenerationScheduler.java}를 테스트 소스에 그대로 복사한
 * {@link LegacyReportGenerationScheduler}다 - 순차 for문 하나만 다르고 나머지 로직은 동일함.
 *
 * <p>LLM 호출(가장 느린 I/O 구간)마다 150ms 인위 지연을 줘서 실제 운영에서의 "location당 수 초"를
 * 축소 재현한다. 월간이 아니라 주간(WEEKLY)으로 호출해 피크 패턴 판단(2번째 LLM 호출)이 개입하지
 * 않게 해서, location당 정확히 LLM 호출 1회(150ms)만 발생하는 순수한 비교가 되도록 한다.
 */
class ReportGenerationSchedulerConcurrencyBenchmarkTest {

    private static final int LOCATION_COUNT = 20;
    private static final long SIMULATED_LATENCY_MS = 150;

    @Test
    void 레거시_순차_처리는_location_수에_비례해_시간이_누적된다() throws InterruptedException {
        LegacyReportGenerationScheduler scheduler = buildLegacyScheduler();

        long elapsedMs = timeMillis(scheduler::generateWeeklyReports);

        System.out.printf("[BEFORE·순차] location %d개, LLM 호출당 %dms → 총 소요 %dms%n",
                LOCATION_COUNT, SIMULATED_LATENCY_MS, elapsedMs);

        // 순차 처리이므로 최소 location 수 * 지연시간만큼은 걸려야 함(오차 감안 90%)
        assertThat(elapsedMs).isGreaterThanOrEqualTo((long) (LOCATION_COUNT * SIMULATED_LATENCY_MS * 0.9));
    }

    @Test
    void 가상_스레드_병렬_처리는_동시_처리_상한만큼만_시간이_걸린다() throws InterruptedException {
        ReportGenerationScheduler scheduler = buildFixedScheduler();

        long elapsedMs = timeMillis(scheduler::generateWeeklyReports);

        System.out.printf("[AFTER·가상스레드] location %d개, LLM 호출당 %dms → 총 소요 %dms (약 %.1f배 단축)%n",
                LOCATION_COUNT, SIMULATED_LATENCY_MS, elapsedMs,
                (LOCATION_COUNT * SIMULATED_LATENCY_MS) / (double) Math.max(elapsedMs, 1));

        // MAX_CONCURRENT_REPORTS(10)로 제한되므로 20개면 최소 2배치(약 300ms)는 걸리지만,
        // 순차 처리(3000ms)보다는 확실히 짧아야 함 - 여유 있게 절반 미만으로 검증
        assertThat(elapsedMs).isLessThan((LOCATION_COUNT * SIMULATED_LATENCY_MS) / 2);
    }

    private long timeMillis(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private LegacyReportGenerationScheduler buildLegacyScheduler() {
        HourlyTelemetryStatService hourlyTelemetryStatService = mock(HourlyTelemetryStatService.class);
        EngineAlertService engineAlertService = mock(EngineAlertService.class);
        SuggestionLogService suggestionLogService = mock(SuggestionLogService.class);
        CoreClient coreClient = mock(CoreClient.class);
        ReportService reportService = mock(ReportService.class);
        ChatClient chatClient = mock(ChatClient.class);
        FlowDraftRequester flowDraftRequester = mock(FlowDraftRequester.class);
        FlowActionPromptBuilder flowActionPromptBuilder = mock(FlowActionPromptBuilder.class);
        RuleEngineClient ruleEngineClient = mock(RuleEngineClient.class);

        stubCommonMocks(hourlyTelemetryStatService, engineAlertService, suggestionLogService, coreClient,
                reportService, chatClient, ruleEngineClient);

        return new LegacyReportGenerationScheduler(hourlyTelemetryStatService, engineAlertService,
                suggestionLogService, coreClient, reportService, chatClient, flowDraftRequester,
                flowActionPromptBuilder, ruleEngineClient);
    }

    private ReportGenerationScheduler buildFixedScheduler() {
        HourlyTelemetryStatService hourlyTelemetryStatService = mock(HourlyTelemetryStatService.class);
        EngineAlertService engineAlertService = mock(EngineAlertService.class);
        SuggestionLogService suggestionLogService = mock(SuggestionLogService.class);
        CoreClient coreClient = mock(CoreClient.class);
        ReportService reportService = mock(ReportService.class);
        ChatClient chatClient = mock(ChatClient.class);
        FlowDraftRequester flowDraftRequester = mock(FlowDraftRequester.class);
        FlowActionPromptBuilder flowActionPromptBuilder = mock(FlowActionPromptBuilder.class);
        RuleEngineClient ruleEngineClient = mock(RuleEngineClient.class);

        stubCommonMocks(hourlyTelemetryStatService, engineAlertService, suggestionLogService, coreClient,
                reportService, chatClient, ruleEngineClient);

        return new ReportGenerationScheduler(hourlyTelemetryStatService, engineAlertService,
                suggestionLogService, coreClient, reportService, chatClient, flowDraftRequester,
                flowActionPromptBuilder, ruleEngineClient);
    }

    private void stubCommonMocks(HourlyTelemetryStatService hourlyTelemetryStatService,
            EngineAlertService engineAlertService, SuggestionLogService suggestionLogService,
            CoreClient coreClient, ReportService reportService, ChatClient chatClient,
            RuleEngineClient ruleEngineClient) {

        List<Long> locationIds = LongStream.rangeClosed(1, LOCATION_COUNT).boxed().toList();
        given(hourlyTelemetryStatService.findDistinctLocationIds(any(), any())).willReturn(locationIds);
        given(hourlyTelemetryStatService.summarizePeriod(anyLong(), any(), any()))
                .willReturn(new PeriodTelemetrySummary(1L, OffsetDateTime.now(), OffsetDateTime.now(),
                        Map.of("temperature", 24.0), Map.of(), Map.of(), Map.of(), Map.of()));

        given(coreClient.getLocation(anyLong()))
                .willAnswer(inv -> new LocationResponse(inv.getArgument(0), "location-" + inv.getArgument(0), 1L,
                        AutoControlMode.SUGGESTION));
        given(coreClient.getActuatorRunLogs(any(), any(), any())).willReturn(List.of());
        given(coreClient.getLocationsByGroup(anyLong())).willReturn(List.of());

        given(ruleEngineClient.findFlows(anyLong(), anyLong())).willReturn(List.of());
        given(engineAlertService.summarizePeriod(anyLong(), any(), any()))
                .willReturn(new EngineAlertSummary(0, 0, List.of()));
        given(suggestionLogService.summarizePeriod(anyLong(), any(), any()))
                .willReturn(new SuggestionSummary(0, 0, 0, 0));

        Report savedReport = Report.builder()
                .groupId(1L).locationId(1L).title("t").reportType(ReportType.WEEKLY).content("c").build();
        ReflectionTestUtils.setField(savedReport, "reportId", 1L);
        given(reportService.createReport(any())).willReturn(savedReport);

        // LLM 호출(가장 느린 I/O 구간)마다 실제 지연을 흉내낸다 - 순차 vs 병렬의 차이가 여기서 갈림
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
        given(callResponseSpec.content()).willAnswer(inv -> {
            Thread.sleep(SIMULATED_LATENCY_MS);
            return "생성된 리포트 본문";
        });
    }
}
