package com.insighton.ai.domain.suggestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.ActuatorCommandExecutor;
import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.suggestion.dto.SuggestionDraft;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogCreateRequest;
import com.insighton.ai.domain.suggestion.event.AiSuggestionActionEvent;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SuggestionGenerationSchedulerTest {

    @Mock
    private ActuatorCommandExecutor actuatorCommandExecutor;

    @Mock
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    @Mock
    private CoreClient coreClient;

    @Mock
    private SuggestionLogService suggestionLogService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private SuggestionGenerationScheduler suggestionGenerationScheduler;

    private static final OffsetDateTime CURRENT_HOUR = OffsetDateTime.parse("2026-08-14T14:00:00+09:00");

    @BeforeEach
    void setUp() {
        suggestionGenerationScheduler = new SuggestionGenerationScheduler(
                actuatorCommandExecutor, hourlyTelemetryStatService, coreClient, suggestionLogService, chatClient,
                new JsonMapper());
    }

    private void stubChatClient(SuggestionDraft draft) {
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
        given(callResponseSpec.entity(SuggestionDraft.class)).willReturn(draft);
    }

    private PeriodTelemetrySummary summary(Map<String, Double> avg) {
        return new PeriodTelemetrySummary(42L, CURRENT_HOUR, CURRENT_HOUR, avg, Map.of(), Map.of(), Map.of(),
                Map.of());
    }

    @Test
    void generateOneSuggestion_이번_시간_집계_데이터가_없으면_아무것도_하지_않는다() {
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR, CURRENT_HOUR))
                .willReturn(summary(Map.of()));

        suggestionGenerationScheduler.generateOneSuggestion(42L, CURRENT_HOUR);

        verify(coreClient, never()).getLocation(42L);
        verify(suggestionLogService, never()).create(any());
    }

    @Test
    void generateOneSuggestion_actionNeeded가_false면_제안을_저장하지_않는다() {
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR, CURRENT_HOUR))
                .willReturn(summary(Map.of("temperature", 23.0)));
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR.minusHours(1), CURRENT_HOUR.minusHours(1)))
                .willReturn(summary(Map.of()));
        given(coreClient.getLocation(42L)).willReturn(new LocationResponse(42L, "사무실1", 5L,
                AutoControlMode.SUGGESTION));
        given(coreClient.getWeather(5L)).willReturn(null);
        stubChatClient(new SuggestionDraft(false, null, null, List.of()));

        suggestionGenerationScheduler.generateOneSuggestion(42L, CURRENT_HOUR);

        verify(suggestionLogService, never()).create(any());
        verify(actuatorCommandExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void generateOneSuggestion_SUGGESTION_모드면_대기_상태로_저장하고_즉시_실행하지_않는다() {
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR, CURRENT_HOUR))
                .willReturn(summary(Map.of("temperature", 29.0)));
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR.minusHours(1), CURRENT_HOUR.minusHours(1)))
                .willReturn(summary(Map.of()));
        given(coreClient.getLocation(42L)).willReturn(new LocationResponse(42L, "사무실1", 5L,
                AutoControlMode.SUGGESTION));
        given(coreClient.getWeather(5L)).willReturn(null);
        stubChatClient(new SuggestionDraft(true, "더워요", "에어컨을 켜세요",
                List.of(new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "ON"))));

        suggestionGenerationScheduler.generateOneSuggestion(42L, CURRENT_HOUR);

        ArgumentCaptor<SuggestionLogCreateRequest> captor = ArgumentCaptor.forClass(SuggestionLogCreateRequest.class);
        verify(suggestionLogService).create(captor.capture());
        assertThat(captor.getValue().isAccepted()).isNull();
        verify(actuatorCommandExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void generateOneSuggestion_AI_DIRECT_모드면_즉시_수락하고_Core에_명령을_실행한다() {
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR, CURRENT_HOUR))
                .willReturn(summary(Map.of("temperature", 29.0)));
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR.minusHours(1), CURRENT_HOUR.minusHours(1)))
                .willReturn(summary(Map.of()));
        given(coreClient.getLocation(42L)).willReturn(new LocationResponse(42L, "사무실1", 5L,
                AutoControlMode.AI_DIRECT));
        given(coreClient.getWeather(5L)).willReturn(null);
        stubChatClient(new SuggestionDraft(true, "더워요", "에어컨을 켭니다",
                List.of(new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "ON"))));

        suggestionGenerationScheduler.generateOneSuggestion(42L, CURRENT_HOUR);

        ArgumentCaptor<SuggestionLogCreateRequest> captor = ArgumentCaptor.forClass(SuggestionLogCreateRequest.class);
        verify(suggestionLogService).create(captor.capture());
        assertThat(captor.getValue().isAccepted()).isTrue();

        verify(actuatorCommandExecutor).execute(eq(42L),
                eq(List.of(new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "ON"))), eq(CallerService.AI_SYSTEM));
    }

    @Test
    void generateOneSuggestion_액추에이터_없는_조언은_AI_DIRECT여도_즉시_실행하지_않는다() {
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR, CURRENT_HOUR))
                .willReturn(summary(Map.of("temperature", 23.0)));
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR.minusHours(1), CURRENT_HOUR.minusHours(1)))
                .willReturn(summary(Map.of()));
        given(coreClient.getLocation(42L)).willReturn(new LocationResponse(42L, "사무실1", 5L,
                AutoControlMode.AI_DIRECT));
        given(coreClient.getWeather(5L)).willReturn(null);
        stubChatClient(new SuggestionDraft(true, "환기 추천", "창문을 여세요", List.of()));

        suggestionGenerationScheduler.generateOneSuggestion(42L, CURRENT_HOUR);

        ArgumentCaptor<SuggestionLogCreateRequest> captor = ArgumentCaptor.forClass(SuggestionLogCreateRequest.class);
        verify(suggestionLogService).create(captor.capture());
        assertThat(captor.getValue().isAccepted()).isNull();
        verify(actuatorCommandExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void generateOneSuggestion_날씨_조회가_실패해도_제안_생성은_계속된다() {
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR, CURRENT_HOUR))
                .willReturn(summary(Map.of("temperature", 23.0)));
        given(hourlyTelemetryStatService.summarizePeriod(42L, CURRENT_HOUR.minusHours(1), CURRENT_HOUR.minusHours(1)))
                .willReturn(summary(Map.of()));
        given(coreClient.getLocation(42L)).willReturn(new LocationResponse(42L, "사무실1", 5L,
                AutoControlMode.SUGGESTION));
        given(coreClient.getWeather(5L)).willThrow(new RuntimeException("날씨 API 오류"));
        stubChatClient(new SuggestionDraft(false, null, null, List.of()));

        suggestionGenerationScheduler.generateOneSuggestion(42L, CURRENT_HOUR);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("## 실외 환경");
    }

    @Test
    void generateSuggestions_한_location이_실패해도_나머지는_계속_처리된다() {
        given(hourlyTelemetryStatService.findDistinctLocationIds(any(), any())).willReturn(List.of(1L, 2L));
        given(hourlyTelemetryStatService.summarizePeriod(eq(1L), any(), any()))
                .willThrow(new RuntimeException("집계 조회 실패"));
        given(hourlyTelemetryStatService.summarizePeriod(eq(2L), any(), any())).willReturn(summary(Map.of()));

        suggestionGenerationScheduler.generateSuggestions();

        verify(suggestionLogService, never()).create(any());
        verify(hourlyTelemetryStatService).summarizePeriod(eq(2L), any(), any());
    }

    @Test
    void generateEventTriggeredSuggestion_이벤트_기반으로_즉시_제안을_생성한다() {
        AiSuggestionActionEvent event = new AiSuggestionActionEvent(5L, 42L, 1L, "temperature", 30.0,
                OffsetDateTime.now());
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(
                summary(Map.of("temperature", 30.0)));
        given(coreClient.getLocation(42L)).willReturn(new LocationResponse(42L, "사무실1", 5L,
                AutoControlMode.SUGGESTION));
        given(coreClient.getWeather(5L)).willReturn(null);
        stubChatClient(new SuggestionDraft(true, "더워요", "에어컨을 켜세요",
                List.of(new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "ON"))));

        suggestionGenerationScheduler.generateEventTriggeredSuggestion(event);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("temperature = 30.0");
        verify(suggestionLogService).create(any());
    }
}
