package com.insighton.ai.domain.scheduledtask.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.insighton.ai.adapter.client.ActuatorCommandExecutor;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.domain.scheduledtask.dto.ScheduledActuatorTask;
import com.insighton.ai.domain.suggestion.dto.SuggestionDraft;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 예약 실행 큐의 claim/reclaim 메커니즘만 검증한다(doExecute 성공 경로는 InfluxDB/LLM까지 필요해 범위 밖) - "역직렬화 실패가 배치를 안 죽이는지", "remove() 반환값을
 * 봐서 이중 claim을 막는지", "유효시간 초과 항목이 due로 회수되는지" 세 가지가 오늘 고친 핵심 로직이다.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledActuatorTaskExecutionSchedulerTest {

    private static final String DUE_KEY = ScheduledActuatorTask.REDIS_KEY;
    private static final String PROCESSING_KEY = ScheduledActuatorTask.REDIS_KEY + ":processing";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    @Mock
    private ActuatorCommandExecutor actuatorCommandExecutor;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private QueryApi queryApi;

    private final JsonMapper jsonMapper = new JsonMapper();

    private ScheduledActuatorTaskExecutionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ScheduledActuatorTaskExecutionScheduler(
                redisTemplate, jsonMapper, influxDBClient, hourlyTelemetryStatService,
                actuatorCommandExecutor, chatClient);
        ReflectionTestUtils.setField(scheduler, "bucket", "test-bucket");
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        // reclaim 단계는 매 테스트마다 도는데, 특별히 검증할 게 아니면 항상 빈 결과로 둔다(회수 테스트는 오버라이드).
        lenient().when(zSetOperations.rangeByScore(eq(PROCESSING_KEY), anyDouble(), anyDouble())).thenReturn(Set.of());
    }

    @Test
    void executeDueTasks_역직렬화_실패해도_배치가_죽지_않고_processing에서_제거된다() {
        String malformed = "이건 JSON이 아님";
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of(malformed));
        given(zSetOperations.remove(DUE_KEY, malformed)).willReturn(1L);

        assertThatCode(() -> scheduler.executeDueTasks()).doesNotThrowAnyException();

        verify(zSetOperations).remove(PROCESSING_KEY, malformed);
        verifyNoInteractions(hourlyTelemetryStatService);
    }

    @Test
    void executeDueTasks_다른_인스턴스가_이미_가져간_작업은_처리하지_않는다() {
        String raw = "{\"taskId\":\"t1\",\"groupId\":5,\"locationId\":42,\"purposeText\":null,\"attemptCount\":0}";
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of(raw));
        // remove()가 0을 반환 = 다른 인스턴스가 이미 claim해감
        given(zSetOperations.remove(DUE_KEY, raw)).willReturn(0L);

        scheduler.executeDueTasks();

        verifyNoInteractions(hourlyTelemetryStatService, actuatorCommandExecutor, chatClient);
    }

    @Test
    void executeDueTasks_유효시간_초과된_processing_항목은_due로_회수된다() {
        String timedOut = "{\"taskId\":\"t1\",\"groupId\":5,\"locationId\":42,\"purposeText\":null,\"attemptCount\":0}";
        given(zSetOperations.rangeByScore(eq(PROCESSING_KEY), anyDouble(), anyDouble())).willReturn(Set.of(timedOut));
        given(zSetOperations.remove(PROCESSING_KEY, timedOut)).willReturn(1L);
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of());

        scheduler.executeDueTasks();

        verify(zSetOperations).add(eq(DUE_KEY), eq(timedOut), anyDouble());
    }

    private PeriodTelemetrySummary summaryWithTemperature(Long locationId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new PeriodTelemetrySummary(locationId, now, now, Map.of("temperature", 24.0), Map.of(), Map.of(),
                Map.of(), Map.of());
    }

    private void stubEmptyInflux() {
        given(influxDBClient.getQueryApi()).willReturn(queryApi);
        given(queryApi.query(anyString())).willReturn(List.of());
    }

    private void stubChatDraft(SuggestionDraft draft) {
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
        given(callResponseSpec.entity(SuggestionDraft.class)).willReturn(draft);
    }

    @Test
    void executeDueTasks_조치가_필요하면_액추에이터를_실행하고_ACK한다() {
        ScheduledActuatorTask task = new ScheduledActuatorTask("t1", 5L, 42L, "회의", 0);
        String raw = jsonMapper.writeValueAsString(task);
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of(raw));
        given(zSetOperations.remove(DUE_KEY, raw)).willReturn(1L);
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(
                summaryWithTemperature(42L));
        stubEmptyInflux();
        List<ActuatorAction> actions = List.of(new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "ON"));
        stubChatDraft(new SuggestionDraft(true, "제목", "문구", actions));

        scheduler.executeDueTasks();

        verify(actuatorCommandExecutor).execute(5L, 42L, actions, CallerService.AI_SYSTEM);
        verify(zSetOperations).remove(PROCESSING_KEY, raw);
        verify(zSetOperations, never()).add(eq(DUE_KEY), anyString(), anyDouble());
    }

    @Test
    void executeDueTasks_조치가_불필요하면_액추에이터_실행_없이_ACK한다() {
        ScheduledActuatorTask task = new ScheduledActuatorTask("t1", 5L, 42L, "회의", 0);
        String raw = jsonMapper.writeValueAsString(task);
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of(raw));
        given(zSetOperations.remove(DUE_KEY, raw)).willReturn(1L);
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(
                summaryWithTemperature(42L));
        stubEmptyInflux();
        stubChatDraft(new SuggestionDraft(false, null, null, List.of()));

        scheduler.executeDueTasks();

        verifyNoInteractions(actuatorCommandExecutor);
        verify(zSetOperations).remove(PROCESSING_KEY, raw);
    }

    @Test
    void executeDueTasks_LLM_응답이_없으면_재시도_대기열에_attemptCount를_올려서_등록한다() {
        ScheduledActuatorTask task = new ScheduledActuatorTask("t1", 5L, 42L, null, 0);
        String raw = jsonMapper.writeValueAsString(task);
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of(raw));
        given(zSetOperations.remove(DUE_KEY, raw)).willReturn(1L);
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(
                summaryWithTemperature(42L));
        stubEmptyInflux();
        stubChatDraft(null);

        scheduler.executeDueTasks();

        verify(zSetOperations).remove(PROCESSING_KEY, raw);
        org.mockito.ArgumentCaptor<String> retriedCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(zSetOperations).add(eq(DUE_KEY), retriedCaptor.capture(), anyDouble());
        ScheduledActuatorTask retried = jsonMapper.readValue(retriedCaptor.getValue(), ScheduledActuatorTask.class);
        org.assertj.core.api.Assertions.assertThat(retried.attemptCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(retried.taskId()).isEqualTo("t1");
    }

    @Test
    void executeDueTasks_최대_재시도_횟수에_도달하면_포기하고_재등록하지_않는다() {
        // MAX_ATTEMPTS=3, attemptCount=2 → +1=3 >= 3 이라 포기 분기를 탄다
        ScheduledActuatorTask task = new ScheduledActuatorTask("t1", 5L, 42L, null, 2);
        String raw = jsonMapper.writeValueAsString(task);
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of(raw));
        given(zSetOperations.remove(DUE_KEY, raw)).willReturn(1L);
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(
                summaryWithTemperature(42L));
        stubEmptyInflux();
        stubChatDraft(null);

        scheduler.executeDueTasks();

        verify(zSetOperations).remove(PROCESSING_KEY, raw);
        verify(zSetOperations, never()).add(eq(DUE_KEY), anyString(), anyDouble());
    }

    @Test
    void executeDueTasks_InfluxDB_실시간_값이_있어도_정상적으로_처리한다() {
        ScheduledActuatorTask task = new ScheduledActuatorTask("t1", 5L, 42L, "회의", 0);
        String raw = jsonMapper.writeValueAsString(task);
        given(zSetOperations.rangeByScore(eq(DUE_KEY), anyDouble(), anyDouble())).willReturn(Set.of(raw));
        given(zSetOperations.remove(DUE_KEY, raw)).willReturn(1L);
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(
                summaryWithTemperature(42L));

        given(influxDBClient.getQueryApi()).willReturn(queryApi);
        FluxRecord fluxRecord = mock(FluxRecord.class);
        given(fluxRecord.getField()).willReturn("temperature");
        given(fluxRecord.getValue()).willReturn(24.5);
        FluxTable table = mock(FluxTable.class);
        given(table.getRecords()).willReturn(List.of(fluxRecord));
        given(queryApi.query(anyString())).willReturn(List.of(table));

        stubChatDraft(new SuggestionDraft(false, null, null, List.of()));

        assertThatCode(() -> scheduler.executeDueTasks()).doesNotThrowAnyException();

        verify(zSetOperations).remove(PROCESSING_KEY, raw);
    }
}
