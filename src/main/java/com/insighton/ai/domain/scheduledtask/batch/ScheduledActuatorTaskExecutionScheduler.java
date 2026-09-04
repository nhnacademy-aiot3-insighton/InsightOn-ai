package com.insighton.ai.domain.scheduledtask.batch;

import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.ACTUATOR_COMMANDS;
import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.COMFORT_RANGE;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.insighton.ai.adapter.client.ActuatorCommandExecutor;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.domain.scheduledtask.dto.ScheduledActuatorTask;
import com.insighton.ai.domain.suggestion.dto.SuggestionDraft;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 매분 폴링해 예약 시각이 된 작업만 처리한다. Rule Engine flow가 아니라 사용자가 채팅으로 직접 요청한 1회성 예약이라 승인 절차 없이 바로 실행한다(ActuatorChatTool과 동일하게 채팅
 * 요청 자체가 승인).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledActuatorTaskExecutionScheduler {

    private static final int MAX_ATTEMPTS = 3;
    // ponytail: 고정 10분 창. 센서 발행 주기가 이보다 뜸해지면 늘릴 것.
    private static final int LIVE_WINDOW_MINUTES = 10;
    private static final Set<String> COMFORT_FIELDS = Set.of("temperature", "humidity", "co2");
    // doExecute()가 InfluxDB+LLM+Core를 순차 호출해 lockAtMostFor(1분)보다 오래 걸릴 수 있다 - 이 시간
    // 안에 processing에서 못 빠져나가면 처리하던 인스턴스가 죽은 것으로 보고 다른 인스턴스가 회수한다.
    private static final Duration VISIBILITY_TIMEOUT = Duration.ofMinutes(3);
    private static final String PROCESSING_REDIS_KEY = ScheduledActuatorTask.REDIS_KEY + ":processing";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final InfluxDBClient influxDBClient;
    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final ActuatorCommandExecutor actuatorCommandExecutor;
    private final ChatClient chatClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    /**
     * due(원본) → processing(진행 중, 유효시간 있음) 순서로 원자적 이동 후 처리, 성공하면 processing에서 ACK(제거). "먼저 지우고 나중에 처리"라 크래시 시 유실되던 것과,
     * remove() 결과를 안 봐서 두 인스턴스가 같은 작업을 동시에 처리할 수 있던 것 둘 다 여기서 막는다 - at-least-once + 멱등 소비자(같은 물리 명령을 다시 보내도 안전한 도메인이라
     * 정확히 한 번 대신 이 방식을 택함).
     */
    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "scheduledActuatorTaskExecution", lockAtMostFor = "PT1M", lockAtLeastFor = "PT10S")
    public void executeDueTasks() {
        reclaimTimedOutTasks();

        Set<String> due = redisTemplate.opsForZSet()
                .rangeByScore(ScheduledActuatorTask.REDIS_KEY, 0, OffsetDateTime.now().toEpochSecond());
        if (due == null || due.isEmpty()) {
            return;
        }

        for (String raw : due) {
            if (claim(raw)) {
                processClaimed(raw);
            }
        }
    }

    /**
     * 유효시간이 지나도 processing에 남아있는 작업 = 처리하던 인스턴스가 죽었거나 비정상적으로 오래 걸리는 것으로 보고 due로 되돌린다. "삭제 후 처리" 순서에서 크래시로 영원히 유실되던 문제의
     * 복구 경로.
     */
    private void reclaimTimedOutTasks() {
        Set<String> timedOut = redisTemplate.opsForZSet()
                .rangeByScore(PROCESSING_REDIS_KEY, 0, OffsetDateTime.now().toEpochSecond());
        if (timedOut == null || timedOut.isEmpty()) {
            return;
        }
        for (String raw : timedOut) {
            Long removed = redisTemplate.opsForZSet().remove(PROCESSING_REDIS_KEY, raw);
            if (removed != null && removed > 0) {
                log.warn("예약 작업 처리 시간 초과, 회수해 재시도 대기열로 되돌림 - raw:{}", raw);
                redisTemplate.opsForZSet().add(ScheduledActuatorTask.REDIS_KEY, raw,
                        OffsetDateTime.now().toEpochSecond());
            }
        }
    }

    /**
     * due에서 processing으로 원자적으로 옮긴다. remove()의 반환값(실제로 지운 개수)을 확인해야 같은 작업을 두 인스턴스가 동시에 집어가는 걸 막을 수 있다 - 예전엔 이 값을 안 봐서 두
     * 인스턴스가 같은 raw를 각자의 due 스냅숏에서 보고 둘 다 처리해버릴 수 있었다.
     *
     * @return 이 인스턴스가 claim에 성공했으면 true(다른 인스턴스가 이미 가져갔으면 false)
     */
    private boolean claim(String raw) {
        Long removed = redisTemplate.opsForZSet().remove(ScheduledActuatorTask.REDIS_KEY, raw);
        if (removed == null || removed == 0) {
            return false;
        }
        redisTemplate.opsForZSet().add(PROCESSING_REDIS_KEY, raw,
                OffsetDateTime.now().plus(VISIBILITY_TIMEOUT).toEpochSecond());
        return true;
    }

    private void processClaimed(String raw) {
        ScheduledActuatorTask task;
        try {
            task = jsonMapper.readValue(raw, ScheduledActuatorTask.class);
        } catch (Exception e) {
            // 역직렬화 자체가 안 되면 attemptCount를 읽을 방법이 없어 재시도 대상으로 못 돌린다 -
            // 예전엔 이 예외가 try 밖에서 안 잡혀 배치 전체(나머지 due 항목까지)가 중단됐었다.
            log.error("예약 작업 역직렬화 실패, 복구 불가로 폐기 - raw:{}", raw, e);
            redisTemplate.opsForZSet().remove(PROCESSING_REDIS_KEY, raw);
            return;
        }

        try {
            doExecute(task);
            redisTemplate.opsForZSet().remove(PROCESSING_REDIS_KEY, raw); // 성공 ACK
        } catch (Exception e) {
            log.error("예약 작업 실행 실패 - taskId:{}, attempt:{}", task.taskId(), task.attemptCount(), e);
            redisTemplate.opsForZSet().remove(PROCESSING_REDIS_KEY, raw);
            retryOrGiveUp(task);
        }
    }

    private void retryOrGiveUp(ScheduledActuatorTask task) {
        if (task.attemptCount() + 1 >= MAX_ATTEMPTS) {
            log.error("예약 작업 최대 재시도 초과, 포기 - taskId:{}", task.taskId());
            return;
        }
        ScheduledActuatorTask retried = new ScheduledActuatorTask(
                task.taskId(), task.groupId(), task.locationId(), task.purposeText(), task.attemptCount() + 1);
        redisTemplate.opsForZSet().add(ScheduledActuatorTask.REDIS_KEY,
                jsonMapper.writeValueAsString(retried), OffsetDateTime.now().plusMinutes(1).toEpochSecond());
    }

    private void doExecute(ScheduledActuatorTask task) {
        OffsetDateTime lastCompletedHour = OffsetDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.HOURS).minusHours(1);
        PeriodTelemetrySummary hourly = hourlyTelemetryStatService.summarizePeriod(
                task.locationId(), lastCompletedHour, lastCompletedHour);
        Map<String, Double> live = queryLiveSensorValues(task.locationId());

        SuggestionDraft draft = chatClient.prompt().user(buildPrompt(task, hourly, live))
                .call().entity(SuggestionDraft.class);

        if (!draft.actionNeeded() || draft.actions().isEmpty()) {
            log.info("예약 준비 - 조치 불필요, taskId:{}", task.taskId());
            return;
        }

        actuatorCommandExecutor.execute(task.groupId(), task.locationId(), draft.actions(), CallerService.AI_SYSTEM);
        log.info("예약 준비 실행 완료 - taskId:{}, locationId:{}, 명령 수:{}",
                task.taskId(), task.locationId(), draft.actions().size());
    }

    private Map<String, Double> queryLiveSensorValues(Long locationId) {
        String fieldFilter = COMFORT_FIELDS.stream()
                .map(f -> "r._field == \"" + f + "\"")
                .collect(Collectors.joining(" or "));
        String flux = """
                from(bucket: "%s")
                |> range(start: -%dm)
                |> filter(fn: (r) => r._measurement == "sensor_data" and r.location_id == "%d" and (%s))
                |> group(columns: ["_field"])
                |> mean()
                """.formatted(bucket, LIVE_WINDOW_MINUTES, locationId, fieldFilter);

        Map<String, Double> result = new HashMap<>();
        for (FluxTable table : influxDBClient.getQueryApi().query(flux)) {
            for (FluxRecord record : table.getRecords()) {
                result.put(record.getField(), ((Number) record.getValue()).doubleValue());
            }
        }
        return result;
    }

    private String buildPrompt(ScheduledActuatorTask task, PeriodTelemetrySummary hourly, Map<String, Double> live) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 스마트 오피스의 실내 환경을 쾌적하게 준비하는 AI입니다.\n");
        sb.append("사용자가 곧(목적: ").append(task.purposeText() != null ? task.purposeText() : "미상")
                .append(") 이 공간을 쓸 예정이라 미리 쾌적하게 준비해달라고 요청했습니다. 필요하면 액추에이터를 조작하세요.\n\n");

        sb.append("## 1시간 전 확정 집계\n");
        hourly.metricsAvg().forEach((metric, value) ->
                sb.append("- ").append(metric).append(": ").append(value).append("\n"));

        sb.append("\n## 지금(최근 ").append(LIVE_WINDOW_MINUTES).append("분 평균)\n");
        live.forEach((metric, value) -> sb.append("- ").append(metric).append(": ").append(value).append("\n"));

        sb.append("\n## 쾌적 기준값\n");
        COMFORT_RANGE.forEach((metric, range) ->
                sb.append("- ").append(metric).append(": ").append(range[0]).append("~").append(range[1]).append("\n"));

        sb.append("\n## 조작 가능한 명령과 허용값\n");
        ACTUATOR_COMMANDS.forEach((type, commands) -> {
            sb.append("- ").append(type).append("\n");
            commands.forEach((command, allowedValues) ->
                    sb.append("  - ").append(command).append(": ").append(allowedValues).append("\n"));
        });

        sb.append("\n---\n");
        sb.append("actions 배열의 각 항목(actuatorType/command/commandValue)은 반드시 위 목록에 있는 조합만 쓰세요. ")
                .append("지금 상태가 이미 쾌적 기준값 안이면 actionNeeded=false로 하고 나머지는 비우세요.");
        return sb.toString();
    }
}
