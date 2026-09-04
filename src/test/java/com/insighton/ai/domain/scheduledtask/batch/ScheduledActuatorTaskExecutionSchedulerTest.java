package com.insighton.ai.domain.scheduledtask.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.influxdb.client.InfluxDBClient;
import com.insighton.ai.adapter.client.ActuatorCommandExecutor;
import com.insighton.ai.domain.scheduledtask.dto.ScheduledActuatorTask;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * 예약 실행 큐의 claim/reclaim 메커니즘만 검증한다(doExecute 성공 경로는 InfluxDB/LLM까지 필요해 범위 밖) -
 * "역직렬화 실패가 배치를 안 죽이는지", "remove() 반환값을 봐서 이중 claim을 막는지",
 * "유효시간 초과 항목이 due로 회수되는지" 세 가지가 오늘 고친 핵심 로직이다.
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

    private ScheduledActuatorTaskExecutionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ScheduledActuatorTaskExecutionScheduler(
                redisTemplate, new JsonMapper(), influxDBClient, hourlyTelemetryStatService,
                actuatorCommandExecutor, chatClient);
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
}
